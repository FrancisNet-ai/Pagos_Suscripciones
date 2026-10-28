package com.nuvyra.supportaccess

import com.nuvyra.support.IntegrationTest
import com.nuvyra.support.StoreFixture
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class SupportAccessIntegrationTest : IntegrationTest() {
    @Autowired lateinit var jdbc: JdbcTemplate

    private val reason = "El titular reporta un pedido duplicado"

    @Test
    fun `soporte solo consulta pedidos con autorizacion vigente del titular, motivo y auditoria visible`() {
        val email = uniqueEmail()
        val owner = registerSeller(email = email)
        val businessId = businessIdOf(email)
        val fx = StoreFixture(mockMvc)
        val store = fx.create(owner)
        fx.placeOrder(store, "915600001")
        val admin = loginSuperAdmin()

        // Sin autorización: nada.
        mockMvc.perform(get("/api/v1/admin/businesses/$businessId/support/orders").param("reason", reason).cookie(admin))
            .andExpect(status().isForbidden).andExpect(jsonPath("$.code").value("SUPPORT_ACCESS_REQUIRED"))

        mockMvc.perform(
            post("/api/v1/business/me/support-access").with(csrf()).cookie(owner).contentType(MediaType.APPLICATION_JSON).content("""{"hours":2,"note":"Revisar pedido duplicado"}"""),
        ).andExpect(status().isOk).andExpect(jsonPath("$.active.note").value("Revisar pedido duplicado"))
        mockMvc.perform(get("/api/v1/admin/businesses/$businessId").cookie(admin)).andExpect(jsonPath("$.supportAccessUntil").exists())

        // Motivo obligatorio en cada consulta.
        mockMvc.perform(get("/api/v1/admin/businesses/$businessId/support/orders").cookie(admin)).andExpect(status().isBadRequest)
        mockMvc.perform(get("/api/v1/admin/businesses/$businessId/support/orders").param("reason", reason).cookie(admin))
            .andExpect(status().isOk).andExpect(jsonPath("$.items[0].customerName").value("Carlos Ramírez"))
        mockMvc.perform(get("/api/v1/admin/businesses/$businessId/support/customers").param("reason", reason).cookie(admin))
            .andExpect(status().isOk).andExpect(jsonPath("$.items.length()").value(1))

        // El titular ve cada consulta con su motivo.
        mockMvc.perform(get("/api/v1/business/me/support-access").cookie(owner))
            .andExpect(jsonPath("$.views.length()").value(2))
            .andExpect(jsonPath("$.views[0].reason").value(reason))

        // Al retirar la autorización se cierra el acceso.
        mockMvc.perform(delete("/api/v1/business/me/support-access").with(csrf()).cookie(owner)).andExpect(jsonPath("$.active").doesNotExist())
        mockMvc.perform(get("/api/v1/admin/businesses/$businessId/support/orders").param("reason", reason).cookie(admin)).andExpect(status().isForbidden)
    }

    @Test
    fun `la autorizacion vence sola y solo el titular la gestiona`() {
        val email = uniqueEmail()
        val owner = registerSeller(email = email)
        val businessId = businessIdOf(email)
        val admin = loginSuperAdmin()
        mockMvc.perform(
            post("/api/v1/business/me/support-access").with(csrf()).cookie(owner).contentType(MediaType.APPLICATION_JSON).content("""{"hours":100}"""),
        ).andExpect(status().isBadRequest)
        mockMvc.perform(
            post("/api/v1/business/me/support-access").with(csrf()).cookie(owner).contentType(MediaType.APPLICATION_JSON).content("""{"hours":1}"""),
        ).andExpect(status().isOk)
        jdbc.update("update support_grants set created_at = now() - interval '2 hours', expires_at = now() - interval '1 minute' where business_id = ?", businessId)
        mockMvc.perform(get("/api/v1/admin/businesses/$businessId/support/orders").param("reason", reason).cookie(admin)).andExpect(status().isForbidden)

        // Un vendedor no usa los endpoints de soporte ni ve datos de otro negocio por esta vía.
        val other = registerSeller()
        mockMvc.perform(get("/api/v1/admin/businesses/$businessId/support/orders").param("reason", reason).cookie(other)).andExpect(status().isForbidden)
    }
}
