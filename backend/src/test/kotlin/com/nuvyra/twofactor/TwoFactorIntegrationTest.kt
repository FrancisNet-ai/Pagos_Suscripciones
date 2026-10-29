package com.nuvyra.twofactor

import com.nuvyra.support.IntegrationTest
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant

class TwoFactorIntegrationTest : IntegrationTest() {
    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private fun postJson(path: String, body: String, cookie: Cookie? = null): ResultActions = mockMvc.perform(
        post(path).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body).also { r -> cookie?.let { r.cookie(it) } },
    )

    private fun login(email: String) = postJson("/api/v1/auth/login", """{"email":"$email","password":"clave-segura-123"}""")

    private fun json(result: ResultActions) = objectMapper.readTree(result.andReturn().response.contentAsString)

    private fun codeFor(secret: String, offset: Long = 0) =
        Totp.code(secret.replace(" ", ""), Totp.step(Instant.now().epochSecond) + offset)

    /** Activa la verificación y devuelve el secreto y los códigos de recuperación. */
    private fun enable(cookie: Cookie): Pair<String, List<String>> {
        val secret = json(postJson("/api/v1/auth/2fa/setup", """{"password":"clave-segura-123"}""", cookie).andExpect(status().isOk))
            .get("secret").asString()
        val enabled = json(postJson("/api/v1/auth/2fa/enable", """{"code":"${codeFor(secret)}"}""", cookie).andExpect(status().isOk))
        return secret to enabled.get("recoveryCodes").values().map { it.asString() }
    }

    @Test
    fun `activar exige contrasena y un codigo valido, y cierra las demas sesiones`() {
        val email = uniqueEmail()
        val cookie = registerSeller(email = email)
        val otherDevice = login(email).andExpect(status().isOk).andReturn().response.getCookie("sid")!!

        postJson("/api/v1/auth/2fa/setup", """{"password":"equivocada"}""", cookie).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errors[0].field").value("password"))
        val setup = json(postJson("/api/v1/auth/2fa/setup", """{"password":"clave-segura-123"}""", cookie).andExpect(status().isOk))
        assertThat(setup.get("otpauthUri").asString()).startsWith("otpauth://totp/Nuvyra:")
        assertThat(setup.get("qrDataUrl").asString()).startsWith("data:image/svg+xml;base64,")
        val secret = setup.get("secret").asString()

        postJson("/api/v1/auth/2fa/enable", """{"code":"000000"}""", cookie).andExpect(status().isBadRequest)
        mockMvc.perform(get("/api/v1/auth/2fa").cookie(cookie)).andExpect(jsonPath("$.enabled").value(false))

        postJson("/api/v1/auth/2fa/enable", """{"code":"${codeFor(secret)}"}""", cookie).andExpect(status().isOk)
            .andExpect(jsonPath("$.recoveryCodes.length()").value(10))
            .andExpect(jsonPath("$.otherSessionsClosed").value(1))
        mockMvc.perform(get("/api/v1/auth/me").cookie(otherDevice)).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/api/v1/auth/2fa").cookie(cookie))
            .andExpect(jsonPath("$.enabled").value(true)).andExpect(jsonPath("$.recoveryCodesLeft").value(10))
        mockMvc.perform(get("/api/v1/auth/me").cookie(cookie)).andExpect(jsonPath("$.user.twoFactorEnabled").value(true))
    }

    @Test
    fun `el inicio de sesion pide el segundo factor y no acepta codigos repetidos`() {
        val email = uniqueEmail()
        val (secret, recovery) = enable(registerSeller(email = email))

        val challenge = json(login(email).andExpect(status().isAccepted)).also {
            assertThat(it.get("twoFactorRequired").asBoolean()).isTrue()
        }.get("challengeToken").asString()
        assertThat(login(email).andReturn().response.getCookie("sid")).isNull()

        // El código usado al activar ya no sirve; uno incorrecto tampoco.
        postJson("/api/v1/auth/login/2fa", """{"challengeToken":"$challenge","code":"${codeFor(secret)}"}""").andExpect(status().isBadRequest)
        postJson("/api/v1/auth/login/2fa", """{"challengeToken":"$challenge","code":"123456"}""").andExpect(status().isBadRequest)
        val session = postJson("/api/v1/auth/login/2fa", """{"challengeToken":"$challenge","code":"${codeFor(secret, 1)}"}""")
            .andExpect(status().isOk).andReturn().response.getCookie("sid")!!
        mockMvc.perform(get("/api/v1/auth/me").cookie(session)).andExpect(status().isOk)
        // El desafío es de un solo uso.
        postJson("/api/v1/auth/login/2fa", """{"challengeToken":"$challenge","code":"${codeFor(secret, 1)}"}""").andExpect(status().isUnauthorized)

        // Un código de recuperación sirve una sola vez (con o sin guion, en minúsculas).
        val second = json(login(email)).get("challengeToken").asString()
        postJson("/api/v1/auth/login/2fa", """{"challengeToken":"$second","code":"${recovery[0].lowercase()}"}""").andExpect(status().isOk)
        val third = json(login(email)).get("challengeToken").asString()
        postJson("/api/v1/auth/login/2fa", """{"challengeToken":"$third","code":"${recovery[0].replace("-", "")}"}""").andExpect(status().isBadRequest)
    }

    @Test
    fun `un desafio admite como maximo cinco intentos`() {
        val email = uniqueEmail()
        val (secret, _) = enable(registerSeller(email = email))
        val challenge = json(login(email)).get("challengeToken").asString()
        repeat(5) { postJson("/api/v1/auth/login/2fa", """{"challengeToken":"$challenge","code":"111111"}""").andExpect(status().isBadRequest) }
        postJson("/api/v1/auth/login/2fa", """{"challengeToken":"$challenge","code":"${codeFor(secret, 1)}"}""")
            .andExpect(status().isUnauthorized).andExpect(jsonPath("$.code").value("TWO_FACTOR_CHALLENGE_EXPIRED"))
    }

    @Test
    fun `desactivar exige contrasena y codigo y regenerar invalida los codigos anteriores`() {
        val email = uniqueEmail()
        val cookie = registerSeller(email = email)
        val (secret, recovery) = enable(cookie)

        val fresh = json(postJson("/api/v1/auth/2fa/recovery-codes", """{"code":"${codeFor(secret, 1)}"}""", cookie).andExpect(status().isOk))
            .get("recoveryCodes").values().map { it.asString() }
        assertThat(fresh).hasSize(10).doesNotContainAnyElementsOf(recovery)

        postJson("/api/v1/auth/2fa/disable", """{"password":"equivocada","code":"${fresh[0]}"}""", cookie).andExpect(status().isBadRequest)
        postJson("/api/v1/auth/2fa/disable", """{"password":"clave-segura-123","code":"${recovery[0]}"}""", cookie).andExpect(status().isBadRequest)
        postJson("/api/v1/auth/2fa/disable", """{"password":"clave-segura-123","code":"${fresh[0]}"}""", cookie).andExpect(status().isNoContent)
        login(email).andExpect(status().isOk)
    }

    @Test
    fun `lista las sesiones abiertas y permite cerrar las de otros dispositivos`() {
        val email = uniqueEmail()
        val current = registerSeller(email = email)
        val other = mockMvc.perform(
            post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/130.0 Mobile Safari/537.36")
                .content("""{"email":"$email","password":"clave-segura-123"}"""),
        ).andReturn().response.getCookie("sid")!!

        val sessions = json(mockMvc.perform(get("/api/v1/auth/sessions").cookie(current)).andExpect(status().isOk))
        assertThat(sessions.size()).isEqualTo(2)
        val otherSession = sessions.values().first { !it.get("current").asBoolean() }
        assertThat(otherSession.get("device").asString()).isEqualTo("Chrome en Android")
        val currentId = sessions.values().first { it.get("current").asBoolean() }.get("id").asString()

        mockMvc.perform(delete("/api/v1/auth/sessions/$currentId").with(csrf()).cookie(current)).andExpect(status().isBadRequest)
        // Otra cuenta no puede cerrar sesiones ajenas.
        mockMvc.perform(delete("/api/v1/auth/sessions/${otherSession.get("id").asString()}").with(csrf()).cookie(registerSeller()))
            .andExpect(status().isNotFound)
        mockMvc.perform(delete("/api/v1/auth/sessions/${otherSession.get("id").asString()}").with(csrf()).cookie(current))
            .andExpect(status().isNoContent)
        mockMvc.perform(get("/api/v1/auth/me").cookie(other)).andExpect(status().isUnauthorized)
        postJson("/api/v1/auth/sessions/revoke-others", "{}", current).andExpect(jsonPath("$.closed").value(0))
    }
}
