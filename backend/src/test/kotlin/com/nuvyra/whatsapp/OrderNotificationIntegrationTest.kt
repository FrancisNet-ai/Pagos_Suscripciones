package com.nuvyra.whatsapp

import com.nuvyra.support.FakeWhatsAppGraph
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
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class OrderNotificationIntegrationTest : IntegrationTest() {
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var job: OrderNotificationJob
    lateinit var fx: StoreFixture

    @BeforeEach
    fun setUp() {
        fx = StoreFixture(mockMvc)
        FakeWhatsAppGraph.reset()
    }

    private fun phoneNumberId() = (100_000_000L + (System.nanoTime() % 900_000_000L)).toString()

    private fun connect(seller: Cookie, id: String, orderTemplate: String = "pedido_recibido", paymentTemplate: String = "pago_verificado") =
        mockMvc.perform(
            put("/api/v1/business/me/whatsapp").with(csrf()).cookie(seller).contentType(MediaType.APPLICATION_JSON).content(
                """{"phoneNumberId":"$id","accessToken":"token-de-prueba-123","reminderTemplate":"recordatorio_renovacion",
                   "orderTemplate":"$orderTemplate","paymentTemplate":"$paymentTemplate","templateLanguage":"es","enabled":true}""",
            ),
        ).andExpect(status().isOk)

    /** Pedido con el pago reportado; `consent` indica si el comprador pidió confirmaciones. */
    private fun reported(store: Store, phone: String, consent: Boolean): Pair<String, String> {
        val body = fx.order(store.slug, store.productId, phone = phone, name = "Rosa Díaz", extra = ",\"acceptOrderUpdates\":$consent").andReturn().response.contentAsString
        fx.report(fx.idFrom(body, "publicToken"), reference = "R" + (System.nanoTime() % 100_000_000)).andExpect(status().isOk)
        return fx.latestPaymentId(store.seller)
    }

    private fun kinds(orderId: String) =
        jdbc.queryForList("select kind || ':' || status from order_notifications where order_id = ?::uuid order by kind", String::class.java, orderId)

    @Test
    fun `solo quien lo autorizo recibe la confirmacion del reporte y de la verificacion`() {
        val seller = registerSeller()
        val store = fx.create(seller)
        val id = phoneNumberId()
        connect(seller, id)
        mockMvc.perform(get("/api/v1/public/stores/${store.slug}")).andExpect(jsonPath("$.orderUpdatesByWhatsapp").value(true))

        val (withConsent, payment) = reported(store, "913400001", consent = true)
        val (withoutConsent, _) = reported(store, "913400002", consent = false)
        job.run()
        assertThat(kinds(withConsent)).containsExactly("PAYMENT_RECEIVED:SENT")
        assertThat(kinds(withoutConsent)).isEmpty()
        val message = FakeWhatsAppGraph.messages().single()
        assertThat(message.path).isEqualTo("/$id/messages")
        assertThat(message.body).contains("\"to\":\"51913400001\"", "\"name\":\"pedido_recibido\"", "\"text\":\"Rosa\"", "\"text\":\"S/ 15.00\"")

        mockMvc.perform(post("/api/v1/payments/$payment/verify").with(csrf()).cookie(seller)).andExpect(status().isOk)
        job.run()
        assertThat(kinds(withConsent)).containsExactly("PAYMENT_RECEIVED:SENT", "PAYMENT_VERIFIED:SENT")
        assertThat(FakeWhatsAppGraph.messages().last().body).contains("\"name\":\"pago_verificado\"")
        // Una segunda ejecución no reenvía.
        job.run()
        assertThat(FakeWhatsAppGraph.messages()).hasSize(2)

        // El vendedor ve el estado y el webhook registra la entrega.
        val wamid = jdbc.queryForObject("select provider_message_id from order_notifications where order_id = ?::uuid and kind = 'PAYMENT_VERIFIED'", String::class.java, withConsent)!!
        val body = """{"object":"whatsapp_business_account","entry":[{"id":"1","changes":[{"field":"messages","value":{"messaging_product":"whatsapp","metadata":{"phone_number_id":"$id"},"statuses":[{"id":"$wamid","status":"read","timestamp":"1793000000","recipient_id":"51913400001"}]}}]}]}"""
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec("test-whatsapp-app-secret".toByteArray(), "HmacSHA256")) }
        val signature = "sha256=" + mac.doFinal(body.toByteArray()).joinToString("") { "%02x".format(it) }
        mockMvc.perform(post("/api/v1/public/webhooks/whatsapp").contentType(MediaType.APPLICATION_JSON).content(body).header("X-Hub-Signature-256", signature))
            .andExpect(status().isOk)
        mockMvc.perform(get("/api/v1/orders/$withConsent").cookie(seller))
            .andExpect(jsonPath("$.whatsappUpdates.length()").value(2))
            .andExpect(jsonPath("$.whatsappUpdates[?(@.kind == 'PAYMENT_VERIFIED')].status").value("DELIVERED"))
    }

    @Test
    fun `sin plantilla configurada no se encola ni se ofrece`() {
        val seller = registerSeller()
        val store = fx.create(seller)
        mockMvc.perform(get("/api/v1/public/stores/${store.slug}")).andExpect(jsonPath("$.orderUpdatesByWhatsapp").value(false))
        connect(seller, phoneNumberId(), orderTemplate = "", paymentTemplate = "pago_verificado")
        val (orderId, _) = reported(store, "913400003", consent = true)
        job.run()
        assertThat(kinds(orderId)).isEmpty()
        assertThat(FakeWhatsAppGraph.messages()).isEmpty()
    }
}
