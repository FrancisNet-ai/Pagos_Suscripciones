package com.nuvyra.renewals

import com.nuvyra.subscriptions.SubscriptionExpirationJob
import com.nuvyra.support.IntegrationTest
import com.nuvyra.support.Store
import com.nuvyra.support.StoreFixture
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.net.URLDecoder
import java.time.Duration
import java.time.Instant
import java.util.UUID

class RenewalIntegrationTest : IntegrationTest() {
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var job: RenewalJob
    @Autowired lateinit var expirationJob: SubscriptionExpirationJob
    @Autowired lateinit var tokens: RenewalTokens
    lateinit var fx: StoreFixture

    @BeforeEach
    fun setUp() {
        fx = StoreFixture(mockMvc)
    }

    /** Suscripción activa que vence en `daysLeft` días (más dos horas, para que siga vigente hoy). */
    private fun activeSubscription(store: Store, phone: String, daysLeft: Long): String {
        val orderId = fx.paidOrder(store, phone = phone)
        val sub = jdbc.queryForObject("select id::text from customer_subscriptions where order_id = ?::uuid", String::class.java, orderId)!!
        val start = Instant.now().minus(Duration.ofDays(30 - daysLeft)).plus(Duration.ofHours(2))
        mockMvc.perform(post("/api/v1/subscriptions/$sub/activate").with(csrf()).cookie(store.seller).contentType(MediaType.APPLICATION_JSON).content("""{"startAt":"$start"}"""))
            .andExpect(status().isOk)
        return sub
    }

    private fun requestOf(sub: String) = jdbc.queryForObject("select id from renewal_requests where subscription_id = ?::uuid order by created_at desc limit 1", UUID::class.java, sub)!!

    private fun tokenOf(sub: String) = tokens.tokenFor(requestOf(sub))

    private fun expiresAt(sub: String) = jdbc.queryForObject("select expires_at from customer_subscriptions where id = ?::uuid", java.sql.Timestamp::class.java, sub)!!.toInstant()

    private fun respond(token: String, response: String) =
        mockMvc.perform(post("/api/v1/public/renewals/$token/response").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""{"response":"$response"}"""))

    private fun renewalOrder(token: String) =
        mockMvc.perform(post("/api/v1/public/renewals/$token/orders").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""{"paymentMethod":"YAPE"}"""))

    private fun verifyLatest(seller: Cookie) {
        val (_, paymentId) = fx.latestPaymentId(seller)
        mockMvc.perform(post("/api/v1/payments/$paymentId/verify").with(csrf()).cookie(seller)).andExpect(status().isOk)
    }

    @Test
    fun `crea una solicitud por ciclo con sus avisos, sin duplicar`() {
        val store = fx.create(registerSeller())
        val soon = activeSubscription(store, "911000001", 2)
        val later = activeSubscription(store, "911000002", 15)

        assertThat(job.openDueRequests()).isGreaterThanOrEqualTo(1)
        assertThat(job.openDueRequests()).isZero()
        assertThat(jdbc.queryForObject("select count(*) from renewal_requests where subscription_id = ?::uuid", Int::class.java, soon)).isEqualTo(1)
        assertThat(jdbc.queryForObject("select count(*) from renewal_requests where subscription_id = ?::uuid", Int::class.java, later)).isZero()
        val types = jdbc.queryForList("select notification_type from renewal_notifications where renewal_request_id = ?", String::class.java, requestOf(soon))
        assertThat(types).containsExactlyInAnyOrder("REMINDER_BEFORE", "REMINDER_DUE_DAY")
    }

    @Test
    fun `el aviso manual usa el numero del cliente y un enlace sin datos personales`() {
        val store = fx.create(registerSeller())
        val sub = activeSubscription(store, "911000003", 2)
        job.openDueRequests()

        val body = mockMvc.perform(get("/api/v1/renewals/notifications").cookie(store.seller))
            .andExpect(jsonPath("$.totalItems").value(1))
            .andExpect(jsonPath("$.items[0].typeLabel").value("Recordatorio previo"))
            .andExpect(jsonPath("$.items[0].consent").value(false))
            .andReturn().response.contentAsString
        val url = Regex("\"whatsappUrl\":\"([^\"]+)\"").find(body)!!.groupValues[1]
        assertThat(url).startsWith("https://wa.me/51911000003?text=")
        val text = URLDecoder.decode(url.substringAfter("text="), Charsets.UTF_8)
        assertThat(text).contains("Servicio Premium", "Tienda de prueba", "/renovar/${tokenOf(sub)}")
        assertThat(text.substringAfter("/renovar/")).doesNotContain("911000003")

        val id = fx.idFrom(body)
        mockMvc.perform(post("/api/v1/renewals/notifications/$id/sent").with(csrf()).cookie(store.seller)).andExpect(status().isOk)
        mockMvc.perform(post("/api/v1/renewals/notifications/$id/sent").with(csrf()).cookie(store.seller))
            .andExpect(status().isConflict).andExpect(jsonPath("$.code").value("NOTIFICATION_PROCESSED"))
        mockMvc.perform(post("/api/v1/renewals/notifications/$id/sent").with(csrf()).cookie(registerSeller())).andExpect(status().isNotFound)
    }

    @Test
    fun `aceptar no renueva y el pago verificado amplia la vigencia conservando el mismo cupo`() {
        val store = fx.create(registerSeller(), capacity = 1)
        val sub = activeSubscription(store, "911000004", 2)
        job.openDueRequests()
        val token = tokenOf(sub)
        val before = expiresAt(sub)

        mockMvc.perform(get("/api/v1/public/renewals/$token"))
            .andExpect(jsonPath("$.productName").value("Servicio Premium"))
            .andExpect(jsonPath("$.customerFirstName").value("Carlos"))
            .andExpect(jsonPath("$.canRespond").value(true))
        respond(token, "ACCEPTED").andExpect(status().isOk).andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"))
        assertThat(expiresAt(sub)).isEqualTo(before)
        assertThat(jdbc.queryForObject("select status from customer_subscriptions where id = ?::uuid", String::class.java, sub)).isEqualTo("RENEWAL_PENDING")

        // El producto está agotado (1 cupo, ocupado por este mismo cliente) y aun así puede renovar.
        mockMvc.perform(get("/api/v1/public/stores/${store.slug}/products")).andExpect(jsonPath("$[0].soldOut").value(true))
        val orderToken = fx.idFrom(renewalOrder(token).andExpect(status().isCreated).andReturn().response.contentAsString, "publicToken")
        fx.report(orderToken, reference = "REN00001").andExpect(status().isOk)
        verifyLatest(store.seller)

        assertThat(expiresAt(sub)).isEqualTo(before.plus(Duration.ofDays(30)))
        assertThat(jdbc.queryForObject("select status from customer_subscriptions where id = ?::uuid", String::class.java, sub)).isEqualTo("ACTIVE")
        assertThat(jdbc.queryForObject("select status from renewal_requests where subscription_id = ?::uuid", String::class.java, sub)).isEqualTo("COMPLETED")
        assertThat(jdbc.queryForObject("select count(*) from subscription_renewals where subscription_id = ?::uuid", Int::class.java, sub)).isEqualTo(1)
        assertThat(jdbc.queryForObject("select count(*) from capacity_allocations where product_id = ?::uuid and allocation_status in ('OCCUPIED','RESERVED')", Int::class.java, store.productId)).isEqualTo(1)
        assertThat(jdbc.queryForObject("select count(*) from renewal_notifications where renewal_request_id = ? and delivery_status = 'PENDING'", Int::class.java, requestOf(sub))).isZero()
        mockMvc.perform(get("/api/v1/reports/dashboard").cookie(store.seller)).andExpect(jsonPath("$.renewalsMonth.count").value(1))
    }

    @Test
    fun `el pedido de renovacion usa el precio vigente y no se duplica`() {
        val store = fx.create(registerSeller())
        val sub = activeSubscription(store, "911000005", 2)
        job.openDueRequests()
        mockMvc.perform(
            put("/api/v1/products/${store.productId}").with(csrf()).cookie(store.seller).contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Servicio Premium","productType":"SUBSCRIPTION","price":18.00,"durationDays":30,"capacityEnabled":false,"capacity":null,"available":true}"""),
        )
        val token = tokenOf(sub)
        val first = renewalOrder(token).andReturn().response.contentAsString
        val second = renewalOrder(token).andReturn().response.contentAsString
        assertThat(fx.idFrom(first, "orderNumber")).isEqualTo(fx.idFrom(second, "orderNumber"))
        mockMvc.perform(get("/api/v1/public/orders/${fx.idFrom(second, "publicToken")}")).andExpect(jsonPath("$.amount").value(18.0))
        assertThat(
            jdbc.queryForObject(
                "select kind from orders where order_number = ? and business_id = (select business_id from products where id = ?::uuid)",
                String::class.java, fx.idFrom(first, "orderNumber"), store.productId,
            ),
        ).isEqualTo("RENEWAL")
    }

    @Test
    fun `rechazar mantiene el servicio hasta vencer, prepara la despedida y luego deja el cupo por liberar`() {
        val store = fx.create(registerSeller(), capacity = 2)
        val sub = activeSubscription(store, "911000006", 2)
        job.openDueRequests()
        respond(tokenOf(sub), "DECLINED")
            .andExpect(jsonPath("$.status").value("DECLINED"))
            .andExpect(jsonPath("$.farewellMessage").value(org.hamcrest.Matchers.startsWith("Gracias por haber confiado en Tienda de prueba, Carlos.")))
        assertThat(jdbc.queryForObject("select status from customer_subscriptions where id = ?::uuid", String::class.java, sub)).isEqualTo("ACTIVE")

        val notifications = jdbc.queryForList("select notification_type || ':' || delivery_status from renewal_notifications where renewal_request_id = ?", String::class.java, requestOf(sub))
        assertThat(notifications).contains("FAREWELL:PENDING", "REMINDER_BEFORE:SKIPPED", "REMINDER_DUE_DAY:SKIPPED")
        respond(tokenOf(sub), "ACCEPTED").andExpect(status().isConflict)

        jdbc.update("update customer_subscriptions set expires_at = now() - interval '1 hour' where id = ?::uuid", sub)
        expirationJob.expireOverdue()
        mockMvc.perform(get("/api/v1/subscriptions/$sub").cookie(store.seller))
            .andExpect(jsonPath("$.status").value("EXPIRED"))
            .andExpect(jsonPath("$.capacityStatus").value("RELEASE_PENDING"))
        mockMvc.perform(get("/api/v1/subscriptions").cookie(store.seller).param("filter", "RELEASE_PENDING")).andExpect(jsonPath("$.totalItems").value(1))
    }

    @Test
    fun `sin pago tras el plazo de gracia la renovacion se cierra y el cupo no queda retenido`() {
        val store = fx.create(registerSeller(), capacity = 1)
        val sub = activeSubscription(store, "911000007", 1)
        job.openDueRequests()
        respond(tokenOf(sub), "ACCEPTED")
        jdbc.update("update customer_subscriptions set expires_at = now() - interval '4 days' where id = ?::uuid", sub)
        jdbc.update("update renewal_requests set cycle_expires_at = now() - interval '4 days' where subscription_id = ?::uuid", sub)

        assertThat(job.expireStale()).isGreaterThanOrEqualTo(1)
        assertThat(job.expireStale()).isZero()
        assertThat(jdbc.queryForObject("select status from renewal_requests where subscription_id = ?::uuid", String::class.java, sub)).isEqualTo("EXPIRED")
        mockMvc.perform(get("/api/v1/subscriptions/$sub").cookie(store.seller))
            .andExpect(jsonPath("$.status").value("EXPIRED"))
            .andExpect(jsonPath("$.capacityStatus").value("RELEASE_PENDING"))
        mockMvc.perform(get("/api/v1/renewals").cookie(store.seller).param("bucket", "NO_RESPONSE")).andExpect(jsonPath("$.totalItems").value(1))
    }

    @Test
    fun `renovar despues de vencer (dentro de la gracia) reactiva desde hoy con su cupo`() {
        val store = fx.create(registerSeller(), capacity = 1)
        val sub = activeSubscription(store, "911000008", 1)
        job.openDueRequests()
        jdbc.update("update customer_subscriptions set expires_at = now() - interval '1 day' where id = ?::uuid", sub)
        jdbc.update("update renewal_requests set cycle_expires_at = now() - interval '1 day' where subscription_id = ?::uuid", sub)
        expirationJob.expireOverdue()

        val token = tokenOf(sub)
        respond(token, "ACCEPTED").andExpect(status().isOk)
        mockMvc.perform(get("/api/v1/subscriptions/$sub").cookie(store.seller)).andExpect(jsonPath("$.capacityStatus").value("OCCUPIED"))
        fx.report(fx.idFrom(renewalOrder(token).andReturn().response.contentAsString, "publicToken"), reference = "REN00002")
        verifyLatest(store.seller)
        mockMvc.perform(get("/api/v1/subscriptions/$sub").cookie(store.seller))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.daysRemaining").value(30))
    }

    @Test
    fun `el cliente puede dejar de recibir avisos desde el enlace`() {
        val store = fx.create(registerSeller())
        val sub = activeSubscription(store, "911000009", 2)
        job.openDueRequests()
        mockMvc.perform(post("/api/v1/public/renewals/${tokenOf(sub)}/opt-out").with(csrf())).andExpect(jsonPath("$.optedOut").value(true))
        mockMvc.perform(get("/api/v1/renewals/notifications").cookie(store.seller)).andExpect(jsonPath("$.totalItems").value(0))
        val customerId = jdbc.queryForObject("select customer_id::text from customer_subscriptions where id = ?::uuid", String::class.java, sub)
        mockMvc.perform(get("/api/v1/customers/$customerId").cookie(store.seller)).andExpect(jsonPath("$.notificationsOptedOut").value(true))
    }

    @Test
    fun `enlaces invalidos o vencidos no revelan nada`() {
        mockMvc.perform(get("/api/v1/public/renewals/corto")).andExpect(status().isNotFound)
        mockMvc.perform(get("/api/v1/public/renewals/" + "A".repeat(43))).andExpect(status().isNotFound)
        val store = fx.create(registerSeller())
        val sub = activeSubscription(store, "911000010", 2)
        job.openDueRequests()
        jdbc.update("update renewal_requests set token_expires_at = now() - interval '1 minute' where subscription_id = ?::uuid", sub)
        mockMvc.perform(get("/api/v1/public/renewals/${tokenOf(sub)}"))
            .andExpect(status().isGone).andExpect(jsonPath("$.code").value("RENEWAL_LINK_EXPIRED"))
    }

    @Test
    fun `ajustes de recordatorio validados y aplicados a nuevas solicitudes`() {
        val store = fx.create(registerSeller())
        mockMvc.perform(put("/api/v1/renewals/settings").with(csrf()).cookie(store.seller).contentType(MediaType.APPLICATION_JSON)
            .content("""{"reminderDaysBefore":20,"reminderOnDueDay":true,"renewalGraceDays":3}""")).andExpect(status().isBadRequest)
        mockMvc.perform(put("/api/v1/renewals/settings").with(csrf()).cookie(store.seller).contentType(MediaType.APPLICATION_JSON)
            .content("""{"reminderDaysBefore":0,"reminderOnDueDay":true,"renewalGraceDays":5}"""))
            .andExpect(status().isOk).andExpect(jsonPath("$.renewalGraceDays").value(5))
        val sub = activeSubscription(store, "911000011", 0)
        job.openDueRequests()
        val types = jdbc.queryForList("select notification_type from renewal_notifications where renewal_request_id = ?", String::class.java, requestOf(sub))
        assertThat(types).containsExactly("REMINDER_DUE_DAY")
    }
}
