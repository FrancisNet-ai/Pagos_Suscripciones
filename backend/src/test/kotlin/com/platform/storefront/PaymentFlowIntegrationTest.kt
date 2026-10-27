package com.platform.storefront

import com.platform.orders.OrderExpirationJob
import com.platform.support.IntegrationTest
import com.platform.support.StoreFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PaymentFlowIntegrationTest : IntegrationTest() {
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var expirationJob: OrderExpirationJob
    lateinit var fx: StoreFixture

    @BeforeEach
    fun setUp() {
        fx = StoreFixture(mockMvc)
    }

    private fun verify(seller: jakarta.servlet.http.Cookie, paymentId: String) =
        mockMvc.perform(post("/api/v1/payments/$paymentId/verify").with(csrf()).cookie(seller))

    private fun reject(seller: jakarta.servlet.http.Cookie, paymentId: String, reason: String = "No encuentro la operación en Yape") =
        mockMvc.perform(post("/api/v1/payments/$paymentId/reject").with(csrf()).cookie(seller).contentType(MediaType.APPLICATION_JSON).content("""{"reason":"$reason"}"""))

    @Test
    fun `reportar el pago no lo verifica y solo el vendedor lo aprueba`() {
        val store = fx.create(registerSeller(), capacity = 2)
        val token = fx.placeOrder(store)

        fx.report(token, reference = "op-778899", receipt = pngBytes())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("PAYMENT_REPORTED"))
            .andExpect(jsonPath("$.statusLabel").value("Pendiente de verificación"))
            .andExpect(jsonPath("$.payment.status").value("PENDING_VERIFICATION"))
            .andExpect(jsonPath("$.payment.transactionReference").value("OP-778899"))
            .andExpect(jsonPath("$.canReportPayment").value(false))
        fx.report(token).andExpect(status().isConflict).andExpect(jsonPath("$.code").value("PAYMENT_ALREADY_REPORTED"))

        val (orderId, paymentId) = fx.latestPaymentId(store.seller)
        mockMvc.perform(get("/api/v1/orders").cookie(store.seller).param("filter", "TO_VERIFY"))
            .andExpect(jsonPath("$.totalItems").value(1))
            .andExpect(jsonPath("$.items[0].statusLabel").value("Por verificar"))
        mockMvc.perform(get("/api/v1/orders/summary").cookie(store.seller)).andExpect(jsonPath("$.toVerify").value(1))

        verify(store.seller, paymentId)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("PAID"))
            .andExpect(jsonPath("$.payments[0].status").value("VERIFIED"))
            .andExpect(jsonPath("$.capacityStatus").value("OCCUPIED"))
        verify(store.seller, paymentId).andExpect(status().isConflict).andExpect(jsonPath("$.code").value("PAYMENT_ALREADY_PROCESSED"))

        mockMvc.perform(get("/api/v1/public/orders/$token"))
            .andExpect(jsonPath("$.statusLabel").value("Pago verificado"))
            .andExpect(jsonPath("$.whatsappUrl").value(org.hamcrest.Matchers.containsString("Pago%20verificado")))
        val actions = jdbc.queryForList("select action from audit_logs where entity_id in (select id from customer_payments where order_id = ?::uuid)", String::class.java, orderId)
        assertThat(actions).contains("PAYMENT_REPORTED", "PAYMENT_VERIFIED")
    }

    @Test
    fun `dos aprobaciones simultaneas del mismo pago solo aplican una`() {
        val store = fx.create(registerSeller())
        fx.report(fx.placeOrder(store))
        val (_, paymentId) = fx.latestPaymentId(store.seller)

        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(4)
        val results = (1..4).map { pool.submit<Int> { start.await(); verify(store.seller, paymentId).andReturn().response.status } }
        start.countDown()
        val statuses = results.map { it.get(30, TimeUnit.SECONDS) }
        pool.shutdown()
        assertThat(statuses.count { it == 200 }).isEqualTo(1)
        assertThat(statuses.count { it == 409 }).isEqualTo(3)
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'PAYMENT_VERIFIED' and entity_id = ?::uuid", Int::class.java, paymentId)).isEqualTo(1)
    }

    @Test
    fun `rechazar permite al comprador corregir y volver a reportar`() {
        val store = fx.create(registerSeller(), capacity = 1)
        val token = fx.placeOrder(store)
        fx.report(token, reference = "11112222")
        val (_, firstPayment) = fx.latestPaymentId(store.seller)

        reject(store.seller, firstPayment, "no").andExpect(status().isBadRequest)
        reject(store.seller, firstPayment)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"))
            .andExpect(jsonPath("$.payments[0].status").value("REJECTED"))
        mockMvc.perform(get("/api/v1/public/orders/$token"))
            .andExpect(jsonPath("$.statusLabel").value("Pago rechazado"))
            .andExpect(jsonPath("$.payment.rejectionReason").value("No encuentro la operación en Yape"))
            .andExpect(jsonPath("$.canReportPayment").value(true))

        fx.report(token, reference = "11113333").andExpect(status().isOk)
        val (orderId, secondPayment) = fx.latestPaymentId(store.seller)
        assertThat(secondPayment).isNotEqualTo(firstPayment)
        mockMvc.perform(get("/api/v1/orders/$orderId").cookie(store.seller)).andExpect(jsonPath("$.payments.length()").value(2))
        verify(store.seller, secondPayment).andExpect(status().isOk)
    }

    @Test
    fun `detecta codigos de operacion duplicados en el mismo negocio y metodo`() {
        val store = fx.create(registerSeller())
        fx.report(fx.placeOrder(store, phone = "911000001"), reference = "99887766").andExpect(status().isOk)
        fx.report(fx.placeOrder(store, phone = "911000002"), reference = "99887766")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("DUPLICATE_REFERENCE"))
        // En otro negocio el mismo código no colisiona.
        val other = fx.create(registerSeller())
        fx.report(fx.placeOrder(other), reference = "99887766").andExpect(status().isOk)
    }

    @Test
    fun `el comprobante es privado y solo lo descarga el vendedor del pedido`() {
        val store = fx.create(registerSeller())
        fx.report(fx.placeOrder(store), receipt = pngBytes()).andExpect(status().isOk)
        val (_, paymentId) = fx.latestPaymentId(store.seller)
        val key = jdbc.queryForObject("select receipt_key from customer_payments where id = ?::uuid", String::class.java, paymentId)!!

        mockMvc.perform(get("/api/v1/public/media/$key")).andExpect(status().isNotFound)
        mockMvc.perform(get("/api/v1/payments/$paymentId/receipt")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/api/v1/payments/$paymentId/receipt").cookie(registerSeller())).andExpect(status().isNotFound)
        mockMvc.perform(get("/api/v1/payments/$paymentId/receipt").cookie(store.seller))
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Type", "image/png"))
            .andExpect(header().string("Cache-Control", "no-store"))

        fx.report(fx.placeOrder(store, phone = "911000009"), reference = "70707070", receipt = "%PDF-1.4 falso".toByteArray())
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_IMAGE"))
    }

    @Test
    fun `un vendedor no puede ver ni verificar pedidos de otro`() {
        val storeA = fx.create(registerSeller())
        fx.report(fx.placeOrder(storeA))
        val (orderId, paymentId) = fx.latestPaymentId(storeA.seller)
        val sellerB = registerSeller()

        mockMvc.perform(get("/api/v1/orders/$orderId").cookie(sellerB)).andExpect(status().isNotFound)
        verify(sellerB, paymentId).andExpect(status().isNotFound)
        reject(sellerB, paymentId).andExpect(status().isNotFound)
        mockMvc.perform(get("/api/v1/orders").cookie(sellerB)).andExpect(jsonPath("$.totalItems").value(0))
        mockMvc.perform(get("/api/v1/orders/$orderId").cookie(storeA.seller)).andExpect(jsonPath("$.status").value("PAYMENT_REPORTED"))
    }

    @Test
    fun `pago tardio sin cupo queda como incidencia y no se puede aprobar`() {
        val store = fx.create(registerSeller(), capacity = 1)
        val late = fx.placeOrder(store, phone = "911000001")
        jdbc.update("update capacity_allocations set reserved_until = now() - interval '1 minute' where allocation_status = 'RESERVED' and product_id = ?::uuid", store.productId)
        fx.report(fx.placeOrder(store, phone = "911000002"), reference = "55550001").andExpect(status().isOk)

        fx.report(late, reference = "55550002").andExpect(status().isOk).andExpect(jsonPath("$.status").value("PAYMENT_REPORTED"))
        val lateOrderId = jdbc.queryForObject(
            "select o.id::text from orders o where o.customer_phone_snapshot = '+51911000001' and o.business_id = (select business_id from products where id = ?::uuid)",
            String::class.java, store.productId,
        )!!
        mockMvc.perform(get("/api/v1/orders/$lateOrderId").cookie(store.seller))
            .andExpect(jsonPath("$.incidentReason").value(org.hamcrest.Matchers.containsString("ya no hay cupos")))
        val latePayment = jdbc.queryForObject("select id::text from customer_payments where order_id = ?::uuid", String::class.java, lateOrderId)!!
        verify(store.seller, latePayment).andExpect(status().isConflict).andExpect(jsonPath("$.code").value("PRODUCT_SOLD_OUT"))
        assertThat(jdbc.queryForObject("select status from customer_payments where id = ?::uuid", String::class.java, latePayment)).isEqualTo("PENDING_VERIFICATION")
    }

    @Test
    fun `cancelar libera la reserva y vencer pedidos es idempotente`() {
        val store = fx.create(registerSeller(), capacity = 1)
        fx.placeOrder(store, phone = "911000001")
        val (orderId, _) = fx.latestPaymentId(store.seller)
        mockMvc.perform(post("/api/v1/orders/$orderId/cancel").with(csrf()).cookie(store.seller).contentType(MediaType.APPLICATION_JSON).content("""{"reason":"Cliente desistió"}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.capacityStatus").value("EXPIRED"))
        mockMvc.perform(get("/api/v1/public/stores/${store.slug}/products")).andExpect(jsonPath("$[0].availableSlots").value(1))

        val token = fx.placeOrder(store, phone = "911000002")
        jdbc.update("update orders set created_at = now() - interval '3 days' where status = 'AWAITING_PAYMENT' and business_id = (select business_id from products where id = ?::uuid)", store.productId)
        assertThat(expirationJob.expireStaleOrders()).isGreaterThanOrEqualTo(1)
        assertThat(expirationJob.expireStaleOrders()).isZero()
        mockMvc.perform(get("/api/v1/public/orders/$token"))
            .andExpect(jsonPath("$.status").value("EXPIRED"))
            .andExpect(jsonPath("$.canReportPayment").value(false))
        fx.report(token).andExpect(status().isConflict).andExpect(jsonPath("$.code").value("ORDER_CLOSED"))
    }

    @Test
    fun `filtra ventas por estado, fecha y busqueda`() {
        val store = fx.create(registerSeller())
        fx.placeOrder(store, phone = "911000001")
        fx.report(fx.placeOrder(store, phone = "922000002"))
        mockMvc.perform(get("/api/v1/orders").cookie(store.seller)).andExpect(jsonPath("$.totalItems").value(2))
        mockMvc.perform(get("/api/v1/orders").cookie(store.seller).param("filter", "AWAITING_PAYMENT")).andExpect(jsonPath("$.totalItems").value(1))
        mockMvc.perform(get("/api/v1/orders").cookie(store.seller).param("query", "922")).andExpect(jsonPath("$.items[0].customerPhone").value("+51922000002"))
        mockMvc.perform(get("/api/v1/orders").cookie(store.seller).param("from", "2000-01-01").param("to", "2000-01-02")).andExpect(jsonPath("$.totalItems").value(0))
        mockMvc.perform(get("/api/v1/orders").cookie(store.seller).param("productId", store.productId)).andExpect(jsonPath("$.totalItems").value(2))
    }
}
