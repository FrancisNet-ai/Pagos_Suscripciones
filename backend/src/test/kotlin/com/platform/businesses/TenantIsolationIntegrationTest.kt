package com.platform.businesses

import com.platform.support.IntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

class TenantIsolationIntegrationTest : IntegrationTest() {
    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Test
    fun `cada vendedor solo obtiene su propio negocio`() {
        val sellerA = registerSeller(businessName = "Digital Streaming")
        val sellerB = registerSeller(businessName = "Edu Digital")

        mockMvc.perform(get("/api/v1/business/me").cookie(sellerA))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.businessName").value("Digital Streaming"))
        mockMvc.perform(get("/api/v1/business/me").cookie(sellerB))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.businessName").value("Edu Digital"))
    }

    @Test
    fun `parametros enviados por el cliente no cambian el negocio resuelto`() {
        val sellerA = registerSeller(businessName = "Negocio A")
        registerSeller(businessName = "Negocio B")
        val businessB = jdbc.queryForObject("select id from businesses where business_name = 'Negocio B' limit 1", UUID::class.java)

        mockMvc.perform(
            get("/api/v1/business/me").cookie(sellerA)
                .param("businessId", businessB.toString())
                .header("X-Business-Id", businessB.toString()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.businessName").value("Negocio A"))
    }

    @Test
    fun `endpoints de administracion SaaS estan cerrados para vendedores`() {
        val seller = registerSeller()
        mockMvc.perform(get("/api/v1/admin/businesses").cookie(seller))
            .andExpect(status().isForbidden)
    }
}
