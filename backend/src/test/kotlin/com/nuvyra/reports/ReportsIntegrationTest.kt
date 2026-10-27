package com.nuvyra.reports

import com.nuvyra.support.IntegrationTest
import com.nuvyra.support.StoreFixture
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import java.time.LocalDate

class ReportsIntegrationTest : IntegrationTest() {
    lateinit var fx: StoreFixture

    @BeforeEach
    fun setUp() {
        fx = StoreFixture(mockMvc)
    }

    @Test
    fun `el panel distingue dinero confirmado de pendiente y no mezcla negocios`() {
        val store = fx.create(registerSeller(), price = "15.00")
        fx.paidOrder(store, phone = "911000001")
        fx.paidOrder(store, phone = "911000001")
        fx.report(fx.placeOrder(store, phone = "911000002"), reference = "PEND0001")
        fx.placeOrder(store, phone = "911000003")
        val other = fx.create(registerSeller(), price = "99.00")
        fx.paidOrder(other)

        mockMvc.perform(get("/api/v1/reports/dashboard").cookie(store.seller))
            .andExpect(jsonPath("$.confirmedToday.amount").value(30.0))
            .andExpect(jsonPath("$.confirmedToday.count").value(2))
            .andExpect(jsonPath("$.confirmedMonth.amount").value(30.0))
            .andExpect(jsonPath("$.pendingVerification.amount").value(15.0))
            .andExpect(jsonPath("$.pendingVerification.count").value(1))
            .andExpect(jsonPath("$.customers").value(3))
            .andExpect(jsonPath("$.subscriptions.pendingActivation").value(2))
            .andExpect(jsonPath("$.last30Days.length()").value(30))
            .andExpect(jsonPath("$.last30Days[29].amount").value(30.0))
            .andExpect(jsonPath("$.recentActivity[0].text").exists())
            .andExpect(jsonPath("$.timeZone").value("America/Lima"))
    }

    @Test
    fun `el reporte de ventas agrupa por producto, estado y clientes recurrentes`() {
        val store = fx.create(registerSeller())
        val curso = fx.createProduct(store.seller, """{"name":"Curso","productType":"ONE_TIME","price":40,"capacityEnabled":false,"available":true}""")
        fx.paidOrder(store, phone = "911000001")
        fx.paidOrder(store, phone = "911000001", productId = curso)
        fx.paidOrder(store, phone = "911000002")
        fx.placeOrder(store, phone = "911000003")

        val today = LocalDate.now().toString()
        mockMvc.perform(get("/api/v1/reports/sales").cookie(store.seller).param("from", today).param("to", today))
            .andExpect(jsonPath("$.confirmed.amount").value(70.0))
            .andExpect(jsonPath("$.confirmed.count").value(3))
            .andExpect(jsonPath("$.byProduct[0].productName").value("Curso"))
            .andExpect(jsonPath("$.byProduct[1].count").value(2))
            .andExpect(jsonPath("$.ordersByStatus[?(@.status == 'PAID')].count").value(3))
            .andExpect(jsonPath("$.ordersByStatus[?(@.status == 'AWAITING_PAYMENT')].count").value(1))
            .andExpect(jsonPath("$.recurringCustomers").value(1))
            .andExpect(jsonPath("$.topRecurring[0].paidOrders").value(2))

        mockMvc.perform(get("/api/v1/reports/sales").cookie(store.seller).param("from", "2020-01-01").param("to", "2020-01-31"))
            .andExpect(jsonPath("$.confirmed.amount").value(0))
            .andExpect(jsonPath("$.byDay.length()").value(31))
    }
}
