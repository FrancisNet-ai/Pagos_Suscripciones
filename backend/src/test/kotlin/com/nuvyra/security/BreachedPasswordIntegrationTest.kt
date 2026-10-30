package com.nuvyra.security

import com.nuvyra.common.config.AppProperties
import com.nuvyra.support.FakePwnedPasswords
import com.nuvyra.support.IntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration

class BreachedPasswordIntegrationTest : IntegrationTest() {
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun pwnedPasswords(registry: DynamicPropertyRegistry) {
            registry.add("app.security.breached-password-check") { "true" }
            registry.add("app.security.pwned-passwords-url") { FakePwnedPasswords.baseUrl }
        }
    }

    @Test
    fun `no se puede registrar ni cambiar a una contrasena filtrada`() {
        mockMvc.perform(
            post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(registerJson(password = FakePwnedPasswords.BREACHED)),
        ).andExpect(status().isBadRequest).andExpect(jsonPath("$.errors[0].field").value("password"))

        // Una contraseña no filtrada pasa; solo viajó el prefijo de 5 caracteres del hash, con relleno.
        val cookie = registerSeller()
        assertThat(FakePwnedPasswords.requests).allSatisfy { (path, padding) ->
            assertThat(path).matches("/range/[0-9A-F]{5}")
            assertThat(padding).isEqualTo("true")
        }

        mockMvc.perform(
            post("/api/v1/auth/password").with(csrf()).cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentPassword":"clave-segura-123","newPassword":"${FakePwnedPasswords.BREACHED}"}"""),
        ).andExpect(status().isBadRequest).andExpect(jsonPath("$.errors[0].field").value("newPassword"))
    }
}

class BreachedPasswordCheckerTest {
    private fun checker(url: String, enabled: Boolean = true) = BreachedPasswordChecker(
        AppProperties(security = AppProperties.Security(breachedPasswordCheck = enabled, pwnedPasswordsUrl = url, breachedPasswordTimeout = Duration.ofSeconds(1))),
    )

    @Test
    fun `cuenta las apariciones y responde cero si no esta filtrada`() {
        assertThat(checker(FakePwnedPasswords.baseUrl).timesBreached(FakePwnedPasswords.BREACHED)).isEqualTo(1234)
        assertThat(checker(FakePwnedPasswords.baseUrl).timesBreached("una frase larga que nadie uso 7391")).isZero()
    }

    @Test
    fun `si el servicio no responde o esta apagado acepta la contrasena`() {
        val unreachable = checker("http://127.0.0.1:1")
        assertThat(unreachable.timesBreached(FakePwnedPasswords.BREACHED)).isNull()
        unreachable.ensureNotBreached(FakePwnedPasswords.BREACHED, "password")
        assertThat(checker(FakePwnedPasswords.baseUrl, enabled = false).timesBreached(FakePwnedPasswords.BREACHED)).isNull()
    }
}
