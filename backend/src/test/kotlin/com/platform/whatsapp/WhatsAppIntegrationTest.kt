package com.platform.whatsapp

import com.platform.renewals.RenewalDispatchJob
import com.platform.renewals.RenewalJob
import com.platform.support.FakeWhatsAppGraph
import com.platform.support.IntegrationTest
import com.platform.support.Store
import com.platform.support.StoreFixture
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class WhatsAppIntegrationTest : IntegrationTest() {
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var renewalJob: RenewalJob
    @Autowired lateinit var dispatchJob: RenewalDispatchJob
    lateinit var fx: StoreFixture

    @BeforeEach
    fun setUp() {
        fx = StoreFixture(mockMvc)
        FakeWhatsAppGraph.reset()
    }

    private fun phoneNumberId() = (100_000_000L + (System.nanoTime() % 900_000_000L)).toString()

    private fun connect(seller: Cookie, phoneNumberId: String, token: String? = "token-de-prueba-123", enabled: Boolean = true, farewell: String = "despedida_v1"): ResultActions =
        mockMvc.perform(
            put("/api/v1/business/me/whatsapp").with(csrf()).cookie(seller).contentType(MediaType.APPLICATION_JSON).content(
                """{"phoneNumberId":"$phoneNumberId","wabaId":"2233445566","accessToken":${token?.let { "\"$it\"" } ?: "null"},
                   "reminderTemplate":"recordatorio_renovacion","farewellTemplate":"$farewell","templateLanguage":"es","enabled":$enabled}""",
            ),
        )

    /** Compra pagada y activada que vence en 2 días, con o sin autorización de recordatorios. */
    private fun subscription(store: Store, phone: String, consent: Boolean): String {
        val body = fx.order(store.slug, store.productId, phone = phone, name = "María López", extra = ",\"acceptRenewalReminders\":$consent").andReturn().response.contentAsString
        fx.report(fx.idFrom(body, "publicToken"), reference = "R" + (System.nanoTime() % 100_000_000)).andReturn()
        val (orderId, paymentId) = fx.latestPaymentId(store.seller)
        mockMvc.perform(post("/api/v1/payments/$paymentId/verify").with(csrf()).cookie(store.seller)).andExpect(status().isOk)
        val sub = jdbc.queryForObject("select id::text from customer_subscriptions where order_id = ?::uuid", String::class.java, orderId)!!
        val start = Instant.now().minus(Duration.ofDays(28)).plus(Duration.ofHours(2))
        mockMvc.perform(post("/api/v1/subscriptions/$sub/activate").with(csrf()).cookie(store.seller).contentType(MediaType.APPLICATION_JSON).content("""{"startAt":"$start"}"""))
            .andExpect(status().isOk)
        return sub
    }

    private fun notificationOf(sub: String) = jdbc.queryForMap(
        """
        select n.id, n.delivery_status, n.channel, n.provider_message_id, n.attempts, n.failure_reason from renewal_notifications n
        join renewal_requests r on r.id = n.renewal_request_id where r.subscription_id = ?::uuid and n.notification_type = 'REMINDER_BEFORE'
        """.trimIndent(),
        sub,
    )

    private fun sign(body: String, secret: String = "test-whatsapp-app-secret"): String {
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(secret.toByteArray(), "HmacSHA256")) }
        return "sha256=" + mac.doFinal(body.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun webhook(body: String, signature: String? = sign(body)): ResultActions {
        val request = post("/api/v1/public/webhooks/whatsapp").contentType(MediaType.APPLICATION_JSON).content(body)
        signature?.let { request.header("X-Hub-Signature-256", it) }
        return mockMvc.perform(request)
    }

    private fun envelope(phoneNumberId: String, inner: String) =
        """{"object":"whatsapp_business_account","entry":[{"id":"2233445566","changes":[{"field":"messages","value":{"messaging_product":"whatsapp","metadata":{"phone_number_id":"$phoneNumberId"},$inner}}]}]}"""

    @Test
    fun `conectar verifica el numero, cifra el token y nunca lo devuelve`() {
        val seller = registerSeller()
        val id = phoneNumberId()
        connect(seller, id).andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("VERIFIED"))
            .andExpect(jsonPath("$.enabled").value(true))
            .andExpect(jsonPath("$.displayPhone").value("+51 955 000 111"))
            .andExpect(jsonPath("$.accessToken").doesNotExist())
        val stored = jdbc.queryForObject("select access_token_encrypted from whatsapp_integrations where phone_number_id = ?", String::class.java, id)!!
        assertThat(stored).startsWith("v1:").doesNotContain("token-de-prueba-123")
        assertThat(FakeWhatsAppGraph.requests.first().authorization).isEqualTo("Bearer token-de-prueba-123")
        assertThat(FakeWhatsAppGraph.requests.map { it.path }).contains("/2233445566/subscribed_apps")
        mockMvc.perform(get("/api/v1/business/me/whatsapp").cookie(seller)).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("token-de-prueba"))))

        // Editar sin enviar el token conserva el guardado.
        connect(seller, id, token = null).andExpect(jsonPath("$.status").value("VERIFIED"))
        // Otro negocio no puede conectar el mismo número.
        connect(registerSeller(), id).andExpect(status().isBadRequest).andExpect(jsonPath("$.errors[0].field").value("phoneNumberId"))
    }

    @Test
    fun `un token rechazado por Meta deja la integracion sin habilitar`() {
        val seller = registerSeller()
        connect(seller, phoneNumberId(), token = "token-invalido").andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("ERROR"))
            .andExpect(jsonPath("$.enabled").value(false))
            .andExpect(jsonPath("$.lastError").value("Invalid OAuth access token."))
        mockMvc.perform(delete("/api/v1/business/me/whatsapp").with(csrf()).cookie(seller)).andExpect(status().isNoContent)
        mockMvc.perform(get("/api/v1/business/me/whatsapp").cookie(seller)).andExpect(jsonPath("$.configured").value(false))
    }

    @Test
    fun `solo se envia automaticamente a clientes que autorizaron recordatorios`() {
        val seller = registerSeller()
        val store = fx.create(seller)
        val id = phoneNumberId()
        connect(seller, id).andExpect(jsonPath("$.enabled").value(true))
        val withConsent = subscription(store, "911000101", consent = true)
        val withoutConsent = subscription(store, "911000102", consent = false)
        renewalJob.run()
        dispatchJob.run()

        val sent = notificationOf(withConsent)
        assertThat(sent["delivery_status"]).isEqualTo("SENT")
        assertThat(sent["channel"]).isEqualTo("WHATSAPP_API")
        assertThat(sent["provider_message_id"] as String).startsWith("wamid.")
        assertThat(notificationOf(withoutConsent)["delivery_status"]).isEqualTo("PENDING")

        val message = FakeWhatsAppGraph.messages().single()
        assertThat(message.path).isEqualTo("/$id/messages")
        assertThat(message.body).contains("\"to\":\"51911000101\"", "\"name\":\"recordatorio_renovacion\"", "\"text\":\"María\"", "\"text\":\"Servicio Premium\"")
            .contains("\"sub_type\":\"quick_reply\"", "RNW:${sent["id"]}:Y", "RNW:${sent["id"]}:N", "\"sub_type\":\"url\"")

        // En el panel: el que se envía solo no pide acción; el otro sigue para envío manual.
        mockMvc.perform(get("/api/v1/renewals/notifications").cookie(seller))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].customerPhone").value("+51911000102"))
            .andExpect(jsonPath("$.items[0].automatic").value(false))
        // Una segunda ejecución no reenvía nada.
        dispatchJob.run()
        assertThat(FakeWhatsAppGraph.messages()).hasSize(1)
    }

    @Test
    fun `un fallo temporal se reintenta y uno definitivo pasa al envio manual`() {
        val seller = registerSeller()
        val store = fx.create(seller)
        connect(seller, phoneNumberId())
        val a = subscription(store, "911000201", consent = true)
        renewalJob.run()
        FakeWhatsAppGraph.failNext(503, "Servicio no disponible")
        dispatchJob.run()
        assertThat(notificationOf(a)).containsEntry("delivery_status", "PENDING").containsEntry("attempts", 1)

        jdbc.update("update renewal_notifications set scheduled_at = now() where id = ?", notificationOf(a)["id"])
        FakeWhatsAppGraph.failNext(400, "(#132001) Template name does not exist in the translation", code = 132001)
        dispatchJob.run()
        val failed = notificationOf(a)
        assertThat(failed["delivery_status"]).isEqualTo("FAILED")
        assertThat(failed["failure_reason"] as String).contains("Template name does not exist")

        mockMvc.perform(get("/api/v1/renewals/notifications").cookie(seller))
            .andExpect(jsonPath("$.items[0].deliveryStatus").value("FAILED"))
            .andExpect(jsonPath("$.items[0].failureReason").value(org.hamcrest.Matchers.containsString("Template name")))
        mockMvc.perform(post("/api/v1/renewals/notifications/${failed["id"]}/sent").with(csrf()).cookie(seller)).andExpect(status().isOk)
    }

    @Test
    fun `el webhook exige firma y registra la entrega una sola vez`() {
        val seller = registerSeller()
        val store = fx.create(seller)
        val id = phoneNumberId()
        connect(seller, id)
        val sub = subscription(store, "911000301", consent = true)
        renewalJob.run()
        dispatchJob.run()
        val wamid = notificationOf(sub)["provider_message_id"] as String

        mockMvc.perform(get("/api/v1/public/webhooks/whatsapp").param("hub.mode", "subscribe").param("hub.verify_token", "test-verify-token").param("hub.challenge", "12345"))
            .andExpect(status().isOk).andExpect(content().string("12345"))
        mockMvc.perform(get("/api/v1/public/webhooks/whatsapp").param("hub.mode", "subscribe").param("hub.verify_token", "otro").param("hub.challenge", "12345"))
            .andExpect(status().isForbidden)

        val delivered = envelope(id, """"statuses":[{"id":"$wamid","status":"delivered","timestamp":"1793000000","recipient_id":"51911000301"}]""")
        webhook(delivered, signature = "sha256=" + "0".repeat(64)).andExpect(status().isUnauthorized)
        webhook(delivered, signature = null).andExpect(status().isUnauthorized)
        assertThat(notificationOf(sub)["delivery_status"]).isEqualTo("SENT")

        webhook(delivered).andExpect(status().isOk)
        webhook(delivered).andExpect(status().isOk)
        assertThat(notificationOf(sub)["delivery_status"]).isEqualTo("DELIVERED")
        assertThat(jdbc.queryForObject("select count(*) from whatsapp_webhook_events where event_key = ?", Int::class.java, "st:$wamid:delivered")).isEqualTo(1)
    }

    @Test
    fun `responder Si por WhatsApp registra la intencion y envia el enlace de pago, sin renovar`() {
        val seller = registerSeller()
        val store = fx.create(seller)
        val id = phoneNumberId()
        connect(seller, id)
        val sub = subscription(store, "911000401", consent = true)
        renewalJob.run()
        dispatchJob.run()
        val notificationId = notificationOf(sub)["id"]
        FakeWhatsAppGraph.reset()

        // Un número distinto no puede responder por el cliente.
        webhook(envelope(id, """"messages":[{"from":"51999999999","id":"wamid.in1","timestamp":"1793000000","type":"button","button":{"text":"Sí, renovar","payload":"RNW:$notificationId:Y"}}]""")).andExpect(status().isOk)
        assertThat(jdbc.queryForObject("select status from renewal_requests where subscription_id = ?::uuid", String::class.java, sub)).isEqualTo("OPEN")

        webhook(envelope(id, """"messages":[{"from":"51911000401","id":"wamid.in2","timestamp":"1793000000","type":"button","button":{"text":"Sí, renovar","payload":"RNW:$notificationId:Y"}}]""")).andExpect(status().isOk)
        assertThat(jdbc.queryForObject("select status from renewal_requests where subscription_id = ?::uuid", String::class.java, sub)).isEqualTo("AWAITING_PAYMENT")
        // Decir que sí no renueva: la vigencia no cambia hasta verificar el pago.
        assertThat(jdbc.queryForObject("select status from customer_subscriptions where id = ?::uuid", String::class.java, sub)).isEqualTo("RENEWAL_PENDING")
        val reply = FakeWhatsAppGraph.messages().single()
        assertThat(reply.body).contains("\"type\":\"text\"", "/renovar/", "51911000401")
    }

    @Test
    fun `responder No envia la despedida y BAJA excluye de los avisos`() {
        val seller = registerSeller()
        val store = fx.create(seller)
        val id = phoneNumberId()
        connect(seller, id)
        val sub = subscription(store, "911000501", consent = true)
        renewalJob.run()
        dispatchJob.run()
        val notificationId = notificationOf(sub)["id"]

        webhook(envelope(id, """"messages":[{"from":"51911000501","id":"wamid.in3","timestamp":"1793000000","type":"button","button":{"text":"No, gracias","payload":"RNW:$notificationId:N"}}]""")).andExpect(status().isOk)
        assertThat(jdbc.queryForObject("select status from renewal_requests where subscription_id = ?::uuid", String::class.java, sub)).isEqualTo("DECLINED")
        val farewell = jdbc.queryForMap(
            "select n.delivery_status, n.channel from renewal_notifications n join renewal_requests r on r.id = n.renewal_request_id where r.subscription_id = ?::uuid and n.notification_type = 'FAREWELL'",
            sub,
        )
        assertThat(farewell).containsEntry("delivery_status", "SENT").containsEntry("channel", "WHATSAPP_API")
        assertThat(FakeWhatsAppGraph.messages().last().body).contains("Gracias por haber confiado")

        webhook(envelope(id, """"messages":[{"from":"51911000501","id":"wamid.in4","timestamp":"1793000000","type":"text","text":{"body":" baja "}}]""")).andExpect(status().isOk)
        val customerId = jdbc.queryForObject("select customer_id from customer_subscriptions where id = ?::uuid", UUID::class.java, sub)
        assertThat(jdbc.queryForObject("select opted_out from notification_preferences where customer_id = ?", Boolean::class.java, customerId)).isTrue()
    }

    @Test
    fun `sin la funcion de avisos en el plan no se puede conectar`() {
        val email = uniqueEmail()
        val seller = registerSeller(email, withPlan = false)
        jdbc.update("insert into saas_plans (name, price, billing_period, feature_reminders) values ('Sin avisos', 10, 'MONTHLY', false) on conflict (name) do nothing")
        grantPlan(businessIdOf(email), jdbc.queryForObject("select id from saas_plans where name = 'Sin avisos'", UUID::class.java)!!)
        connect(seller, phoneNumberId()).andExpect(status().isForbidden).andExpect(jsonPath("$.code").value("FEATURE_NOT_IN_PLAN"))
    }
}
