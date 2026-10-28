package com.nuvyra.auth

import com.nuvyra.support.IntegrationTest
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class ChangePasswordIntegrationTest : IntegrationTest() {
    private fun login(email: String, password: String) = mockMvc.perform(
        post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""{"email":"$email","password":"$password"}"""),
    )

    private fun change(cookie: Cookie, current: String, next: String) = mockMvc.perform(
        post("/api/v1/auth/password").with(csrf()).cookie(cookie).contentType(MediaType.APPLICATION_JSON)
            .content("""{"currentPassword":"$current","newPassword":"$next"}"""),
    )

    @Test
    fun `cambiar la contrasena exige la actual y cierra las demas sesiones`() {
        val email = uniqueEmail()
        val current = registerSeller(email = email)
        val otherDevice = login(email, "clave-segura-123").andExpect(status().isOk).andReturn().response.getCookie("sid")!!

        change(current, "clave-equivocada", "nueva-clave-segura-1").andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errors[0].field").value("currentPassword"))
        change(current, "clave-segura-123", "clave-segura-123").andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errors[0].field").value("newPassword"))
        change(current, "clave-segura-123", "corta").andExpect(status().isBadRequest)

        change(current, "clave-segura-123", "nueva-clave-segura-1").andExpect(status().isOk)
            .andExpect(jsonPath("$.otherSessionsClosed").value(1))
        // La sesión desde la que se cambió sigue abierta; la del otro dispositivo, no.
        mockMvc.perform(get("/api/v1/auth/me").cookie(current)).andExpect(status().isOk)
        mockMvc.perform(get("/api/v1/auth/me").cookie(otherDevice)).andExpect(status().isUnauthorized)
        login(email, "clave-segura-123").andExpect(status().isUnauthorized)
        login(email, "nueva-clave-segura-1").andExpect(status().isOk)
    }

    @Test
    fun `sin sesion no se puede cambiar la contrasena`() {
        mockMvc.perform(
            post("/api/v1/auth/password").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentPassword":"x","newPassword":"nueva-clave-segura-1"}"""),
        ).andExpect(status().isUnauthorized)
    }
}
