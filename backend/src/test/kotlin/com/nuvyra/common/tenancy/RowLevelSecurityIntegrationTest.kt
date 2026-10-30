package com.nuvyra.common.tenancy

import com.nuvyra.support.IntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

class RowLevelSecurityIntegrationTest : IntegrationTest() {
    @Autowired
    private lateinit var jdbc: JdbcTemplate

    private fun addCustomer(businessId: UUID, phone: String) {
        jdbc.update("insert into customers (business_id, full_name, phone, source) values (?, 'Cliente', ?, 'MANUAL')", businessId, phone)
    }

    private fun count(sql: String, vararg args: Any): Int = jdbc.queryForObject(sql, Int::class.java, *args)!!

    @Test
    fun `con un negocio fijado la base solo deja ver y escribir sus filas`() {
        val emailA = uniqueEmail()
        val emailB = uniqueEmail()
        registerSeller(email = emailA)
        registerSeller(email = emailB)
        val a = businessIdOf(emailA)
        val b = businessIdOf(emailB)
        addCustomer(a, "+51911000001")
        addCustomer(b, "+51911000002")

        TenantContext.runAs(a) {
            assertThat(jdbc.queryForObject("select current_user", String::class.java)).isEqualTo("nuvyra_app")
            // Una consulta que «olvida» filtrar por negocio no ve los clientes de B.
            assertThat(count("select count(*) from customers where business_id in (?, ?)", a, b)).isEqualTo(1)
            assertThat(count("select count(*) from customers where business_id = ?", b)).isZero()
            assertThat(jdbc.update("update customers set notes = 'x' where business_id = ?", b)).isZero()
            assertThatThrownBy { addCustomer(b, "+51911000003") }.rootCause().hasMessageContaining("row-level security")
        }
        // Sin negocio (tareas programadas, webhooks, Flyway) se usa el dueño, como antes.
        assertThat(jdbc.queryForObject("select current_user", String::class.java)).isNotEqualTo("nuvyra_app")
        assertThat(count("select count(*) from customers where business_id in (?, ?)", a, b)).isEqualTo(2)
    }

    @Test
    fun `el panel funciona bajo las politicas y no expone clientes de otro negocio`() {
        val emailA = uniqueEmail()
        val emailB = uniqueEmail()
        val cookieA = registerSeller(email = emailA)
        registerSeller(email = emailB)
        addCustomer(businessIdOf(emailA), "+51911000011")
        addCustomer(businessIdOf(emailB), "+51911000012")
        mockMvc.perform(get("/api/v1/customers").cookie(cookieA))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(1))
    }

    @Test
    fun `solo las rutas del panel fijan el negocio`() {
        assertThat(TenantContextFilter.isPanelPath("/api/v1/customers")).isTrue()
        assertThat(TenantContextFilter.isPanelPath("/api/v1/business/me/staff")).isTrue()
        assertThat(TenantContextFilter.isPanelPath("/api/v1/public/stores/tienda/orders")).isFalse()
        assertThat(TenantContextFilter.isPanelPath("/api/v1/auth/login")).isFalse()
        assertThat(TenantContextFilter.isPanelPath("/api/v1/admin/businesses")).isFalse()
        assertThat(TenantContextFilter.isPanelPath("/actuator/health")).isFalse()
    }
}
