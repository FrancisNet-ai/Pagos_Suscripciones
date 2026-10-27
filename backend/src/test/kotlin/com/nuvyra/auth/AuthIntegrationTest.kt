package com.nuvyra.auth

import com.nuvyra.common.text.SecureTokens
import com.nuvyra.support.CapturingPasswordResetNotifier
import com.nuvyra.support.IntegrationTest
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class AuthIntegrationTest : IntegrationTest() {
    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var notifier: CapturingPasswordResetNotifier

    private fun login(email: String, password: String) = mockMvc.perform(
        post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("""{"email":"$email","password":"$password"}"""),
    )

    @Test
    fun `registro crea usuario, negocio con slug y codigo, y sesion con cookie HttpOnly`() {
        val email = uniqueEmail()
        mockMvc.perform(
            post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(registerJson(email = email.uppercase(), businessName = "Edu Digital Perú", phone = "+51 912 345 678")),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.user.email").value(email))
            .andExpect(jsonPath("$.user.phone").value("+51912345678"))
            .andExpect(jsonPath("$.user.roles[0]").value("BUSINESS_OWNER"))
            .andExpect(jsonPath("$.business.slug").value(org.hamcrest.Matchers.startsWith("edu-digital-peru")))
            .andExpect(jsonPath("$.business.uniqueCode").value(org.hamcrest.Matchers.matchesPattern("NEG-[0-9A-Z]{8}")))
            .andExpect(cookie().httpOnly("sid", true))
            .andExpect(cookie().sameSite("sid", "Lax"))

        val stored = jdbc.queryForObject("select password_hash from users where email = ?", String::class.java, email)!!
        assertThat(stored).startsWith("{bcrypt}").doesNotContain("clave-segura-123")
    }

    @Test
    fun `registro rechaza correo duplicado y celular invalido`() {
        val email = uniqueEmail()
        registerSeller(email)
        mockMvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(registerJson(email = email)))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"))

        mockMvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(registerJson(phone = "12345")))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errors[0].field").value("phone"))
    }

    @Test
    fun `slugs repetidos generan enlaces distintos`() {
        val first = registerSeller(businessName = "Tienda Repetida")
        val second = registerSeller(businessName = "Tienda Repetida")
        val slugA = mockMvc.perform(get("/api/v1/business/me").cookie(first)).andReturn().response.contentAsString
        val slugB = mockMvc.perform(get("/api/v1/business/me").cookie(second)).andReturn().response.contentAsString
        assertThat(slugA).contains("\"slug\":\"tienda-repetida\"")
        assertThat(slugB).containsPattern("\"slug\":\"tienda-repetida-[0-9a-z]{4}\"")
    }

    @Test
    fun `login correcto devuelve la sesion del vendedor`() {
        val email = uniqueEmail()
        registerSeller(email)
        val cookie = login(email, "clave-segura-123")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.business.slug").exists())
            .andReturn().response.getCookie("sid")!!

        mockMvc.perform(get("/api/v1/auth/me").cookie(cookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.user.email").value(email))
    }

    @Test
    fun `contrasena incorrecta y correo inexistente responden igual`() {
        val email = uniqueEmail()
        registerSeller(email)
        login(email, "otra-clave-999").andExpect(status().isUnauthorized).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
        login("nadie@ejemplo.pe", "otra-clave-999").andExpect(status().isUnauthorized).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
    }

    @Test
    fun `login se bloquea tras demasiados intentos para un correo`() {
        val email = uniqueEmail()
        registerSeller(email)
        repeat(10) { login(email, "incorrecta-000") }
        login(email, "clave-segura-123").andExpect(status().isTooManyRequests).andExpect(jsonPath("$.code").value("RATE_LIMITED"))
    }

    @Test
    fun `acceso sin sesion o con cookie falsa es rechazado`() {
        mockMvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
        mockMvc.perform(get("/api/v1/business/me").cookie(Cookie("sid", "token-inventado")))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `sesion vencida por inactividad es rechazada`() {
        val cookie = registerSeller()
        jdbc.update(
            "update user_sessions set idle_expires_at = now() - interval '1 minute' where token_hash = ?",
            SecureTokens.sha256Hex(cookie.value),
        )
        mockMvc.perform(get("/api/v1/auth/me").cookie(cookie)).andExpect(status().isUnauthorized)
    }

    @Test
    fun `peticiones que modifican estado exigen token CSRF`() {
        val cookie = registerSeller()
        mockMvc.perform(post("/api/v1/auth/logout").cookie(cookie))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("CSRF_INVALID"))
    }

    @Test
    fun `logout revoca la sesion`() {
        val cookie = registerSeller()
        mockMvc.perform(post("/api/v1/auth/logout").with(csrf()).cookie(cookie)).andExpect(status().isNoContent)
        mockMvc.perform(get("/api/v1/auth/me").cookie(cookie)).andExpect(status().isUnauthorized)
    }

    @Test
    fun `refresh rota el token e invalida el anterior`() {
        val old = registerSeller()
        val rotated = mockMvc.perform(post("/api/v1/auth/refresh").with(csrf()).cookie(old))
            .andExpect(status().isOk)
            .andReturn().response.getCookie("sid")!!
        assertThat(rotated.value).isNotEqualTo(old.value)
        mockMvc.perform(get("/api/v1/auth/me").cookie(old)).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/api/v1/auth/me").cookie(rotated)).andExpect(status().isOk)
    }

    @Test
    fun `recuperacion de contrasena cambia la clave y revoca sesiones`() {
        val email = uniqueEmail()
        val session = registerSeller(email)

        mockMvc.perform(post("/api/v1/auth/password-reset").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""{"email":"$email"}"""))
            .andExpect(status().isAccepted)
        // Un correo inexistente recibe exactamente la misma respuesta.
        mockMvc.perform(post("/api/v1/auth/password-reset").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""{"email":"nadie@ejemplo.pe"}"""))
            .andExpect(status().isAccepted)

        val token = notifier.links[email]!!.substringAfter("token=")
        val tokenInDb = jdbc.queryForObject("select count(*) from password_reset_tokens where token_hash = ?", Int::class.java, token)
        assertThat(tokenInDb).isZero() // solo se guarda el hash

        val confirm = """{"token":"$token","newPassword":"nueva-clave-segura"}"""
        mockMvc.perform(post("/api/v1/auth/password-reset/confirm").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(confirm))
            .andExpect(status().isNoContent)
        mockMvc.perform(post("/api/v1/auth/password-reset/confirm").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(confirm))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"))

        mockMvc.perform(get("/api/v1/auth/me").cookie(session)).andExpect(status().isUnauthorized)
        login(email, "clave-segura-123").andExpect(status().isUnauthorized)
        login(email, "nueva-clave-segura").andExpect(status().isOk)
    }

    @Test
    fun `los eventos de autenticacion quedan auditados`() {
        val email = uniqueEmail()
        registerSeller(email)
        login(email, "mala-clave-000")
        val actions = jdbc.queryForList(
            "select a.action from audit_logs a join users u on u.id = a.actor_id where u.email = ? order by a.created_at",
            String::class.java, email,
        )
        assertThat(actions).contains("USER_REGISTERED", "LOGIN_FAILED")
    }
}
