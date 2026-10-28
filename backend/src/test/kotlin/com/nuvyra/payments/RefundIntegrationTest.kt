package com.nuvyra.payments

import com.nuvyra.renewals.RenewalJob
import com.nuvyra.renewals.RenewalTokens
import com.nuvyra.support.IntegrationTest
import com.nuvyra.support.Store
import com.nuvyra.support.StoreFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID

class RefundIntegrationTest : IntegrationTest() {
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var renewalJob: RenewalJob
    @Autowired lateinit var tokens: RenewalTokens
    lateinit var fx: StoreFixture

    @BeforeEach
    fun setUp() {
        fx = StoreFixture(mockMvc)
    }

    private fun refund(store: Store, paymentId: String, body: String = """{"reason":"El cliente no pudo usar el servicio","refundReference":"dev123"}""") =
        mockMvc.perform(post("/api/v1/payments/$paymentId/refund").with(csrf()).cookie(store.seller).contentType(MediaType.APPLICATION_JSON).content(body))

    private fun subscriptionOf(orderId: String) =
        jdbc.queryForMap("select s.id, s.status, a.allocation_status from customer_subscriptions s left join capacity_allocations a on a.customer_subscription_id = s.id where s.order_id = ?::uuid", orderId)

    private fun confirmedMonth(store: Store) =
        mockMvc.perform(get("/api/v1/reports/dashboard").cookie(store.seller)).andReturn().response.contentAsString

    @Test
    fun `reembolsar una compra no entregada cancela la suscripcion, libera el cupo y deja de contar como ingreso`() {
        val store = fx.create(registerSeller(), capacity = 1)
        val orderId = fx.paidOrder(store, phone = "912300001")
        val (_, paymentId) = fx.latestPaymentId(store.seller)
        assertThat(confirmedMonth(store)).contains("\"confirmedMonth\":{\"amount\":15.00,\"count\":1}")

        refund(store, paymentId)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("REFUNDED"))
            .andExpect(jsonPath("$.statusLabel").value("Pago reembolsado"))
            .andExpect(jsonPath("$.payments[0].refundReference").value("DEV123"))
        val sub = subscriptionOf(orderId)
        assertThat(sub["status"]).isEqualTo("CANCELLED")
        assertThat(sub["allocation_status"]).isEqualTo("RELEASED")
        assertThat(confirmedMonth(store)).contains("\"confirmedMonth\":{\"amount\":0,\"count\":0}")
        // El cupo liberado vuelve a estar disponible para otro comprador.
        fx.order(store.slug, store.productId, phone = "912300002").andExpect(status().isCreated)
        // No se reembolsa dos veces.
        refund(store, paymentId).andExpect(status().isConflict).andExpect(jsonPath("$.code").value("PAYMENT_NOT_REFUNDABLE"))
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'PAYMENT_REFUNDED' and entity_id = ?::uuid", Int::class.java, paymentId)).isEqualTo(1)
    }

    @Test
    fun `reembolsar un servicio ya entregado deja el acceso por retirar como pendiente`() {
        val store = fx.create(registerSeller(), capacity = 2)
        val orderId = fx.paidOrder(store, phone = "912300003")
        val subId = subscriptionOf(orderId)["id"].toString()
        mockMvc.perform(post("/api/v1/subscriptions/$subId/activate").with(csrf()).cookie(store.seller).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk)
        refund(store, fx.latestPaymentId(store.seller).second).andExpect(status().isOk)

        val sub = subscriptionOf(orderId)
        assertThat(sub["status"]).isEqualTo("CANCELLED")
        assertThat(sub["allocation_status"]).isEqualTo("RELEASE_PENDING")
        mockMvc.perform(get("/api/v1/tasks").cookie(store.seller))
            .andExpect(jsonPath("$.items[?(@.type == 'RELEASE_ACCESS')]").isNotEmpty)
    }

    @Test
    fun `solo se reembolsan pagos verificados, con motivo y solo el titular`() {
        val store = fx.create(registerSeller())
        fx.report(fx.placeOrder(store, "912300004")).andExpect(status().isOk)
        val (_, pending) = fx.latestPaymentId(store.seller)
        refund(store, pending).andExpect(status().isConflict)
        refund(store, UUID.randomUUID().toString()).andExpect(status().isNotFound)
        mockMvc.perform(post("/api/v1/payments/$pending/verify").with(csrf()).cookie(store.seller)).andExpect(status().isOk)
        refund(store, pending, """{"reason":""}""").andExpect(status().isBadRequest)

        // Otro negocio no puede tocarlo.
        val other = fx.create(registerSeller())
        refund(other, pending).andExpect(status().isNotFound)
    }

    @Test
    fun `reembolsar una renovacion devuelve la vigencia anterior`() {
        val store = fx.create(registerSeller())
        val orderId = fx.paidOrder(store, phone = "912300005")
        val sub = subscriptionOf(orderId)["id"].toString()
        val start = Instant.now().minus(Duration.ofDays(28)).plus(Duration.ofHours(2))
        mockMvc.perform(post("/api/v1/subscriptions/$sub/activate").with(csrf()).cookie(store.seller).contentType(MediaType.APPLICATION_JSON)
            .content("""{"startAt":"$start"}""")).andExpect(status().isOk)
        val before = jdbc.queryForObject("select expires_at from customer_subscriptions where id = ?::uuid", Timestamp::class.java, sub)!!.toInstant()

        renewalJob.openDueRequests()
        val request = jdbc.queryForObject("select id from renewal_requests where subscription_id = ?::uuid", UUID::class.java, sub)!!
        val token = tokens.tokenFor(request)
        mockMvc.perform(post("/api/v1/public/renewals/$token/orders").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""{"paymentMethod":"YAPE"}"""))
            .andExpect(status().isCreated)
        val renewalToken = jdbc.queryForObject("select o.id::text from orders o where o.kind = 'RENEWAL' and o.business_id = (select business_id from customer_subscriptions where id = ?::uuid)", String::class.java, sub)!!
        val paymentId = jdbc.queryForObject("select id::text from customer_payments where order_id = ?::uuid", String::class.java, renewalToken)!!
        jdbc.update("update customer_payments set status = 'PENDING_VERIFICATION', transaction_reference = 'REN1', reported_at = now() where id = ?::uuid", paymentId)
        jdbc.update("update orders set status = 'PAYMENT_REPORTED' where id = ?::uuid", renewalToken)
        mockMvc.perform(post("/api/v1/payments/$paymentId/verify").with(csrf()).cookie(store.seller)).andExpect(status().isOk)
        val extended = jdbc.queryForObject("select expires_at from customer_subscriptions where id = ?::uuid", Timestamp::class.java, sub)!!.toInstant()
        assertThat(extended).isAfter(before)

        refund(store, paymentId).andExpect(status().isOk).andExpect(jsonPath("$.status").value("REFUNDED"))
        assertThat(jdbc.queryForObject("select expires_at from customer_subscriptions where id = ?::uuid", Timestamp::class.java, sub)!!.toInstant()).isEqualTo(before)
        assertThat(jdbc.queryForObject("select refunded_at is not null from subscription_renewals where order_id = ?::uuid", Boolean::class.java, renewalToken)).isTrue()
    }
}
