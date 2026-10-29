package com.nuvyra.twofactor

import com.nuvyra.support.IntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

/** Con la configuración de producción, el superadministrador debe activar la verificación para usar /admin. */
@TestPropertySource(properties = ["app.security.require-super-admin-two-factor=true"])
class SuperAdminTwoFactorRequiredTest : IntegrationTest() {

    @Test
    fun `sin verificacion en dos pasos solo puede configurar su cuenta`() {
        val admin = loginSuperAdmin()
        mockMvc.perform(get("/api/v1/auth/me").cookie(admin))
            .andExpect(jsonPath("$.user.twoFactorRequired").value(true)).andExpect(jsonPath("$.user.twoFactorEnabled").value(false))
        mockMvc.perform(get("/api/v1/admin/plans").cookie(admin))
            .andExpect(status().isForbidden).andExpect(jsonPath("$.code").value("TWO_FACTOR_REQUIRED"))

        val setup = mockMvc.perform(
            post("/api/v1/auth/2fa/setup").with(csrf()).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content("""{"password":"clave-admin-segura-1"}"""),
        ).andExpect(status().isOk).andReturn().response.contentAsString
        val secret = Regex("\"secret\":\"([A-Z2-7 ]+)\"").find(setup)!!.groupValues[1].replace(" ", "")
        mockMvc.perform(
            post("/api/v1/auth/2fa/enable").with(csrf()).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content("""{"code":"${Totp.code(secret, Totp.step(Instant.now().epochSecond))}"}"""),
        ).andExpect(status().isOk)

        mockMvc.perform(get("/api/v1/admin/plans").cookie(admin)).andExpect(status().isOk)
        // No puede desactivarla mientras sea obligatoria.
        mockMvc.perform(
            post("/api/v1/auth/2fa/disable").with(csrf()).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content("""{"password":"clave-admin-segura-1","code":"000000"}"""),
        ).andExpect(status().isForbidden)
    }
}
