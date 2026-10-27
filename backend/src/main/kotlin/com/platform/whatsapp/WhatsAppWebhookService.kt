package com.platform.whatsapp

import com.platform.common.config.AppProperties
import com.platform.customers.CustomerRepository
import com.platform.renewals.DeliveryStatus
import com.platform.renewals.NotificationChannel
import com.platform.renewals.NotificationType
import com.platform.renewals.RenewalDispatchService
import com.platform.renewals.RenewalMessages
import com.platform.renewals.RenewalNotificationRepository
import com.platform.renewals.RenewalRequest
import com.platform.renewals.RenewalResponse
import com.platform.renewals.RenewalService
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Webhook de WhatsApp Business Platform. Solo se procesa si la firma `X-Hub-Signature-256` coincide con
 * el secreto de la app; cada evento se registra una vez (Meta reintenta entregas).
 * - `statuses`: entregado, leído o fallido para los avisos enviados.
 * - `messages` de tipo botón: respuesta Sí/No a un recordatorio (se exige que responda el mismo cliente).
 * - Texto «BAJA» o «STOP»: el cliente deja de recibir avisos de ese negocio.
 */
@Service
class WhatsAppWebhookService(
    private val properties: AppProperties,
    private val objectMapper: ObjectMapper,
    private val integrations: WhatsAppIntegrationRepository,
    private val integrationService: WhatsAppIntegrationService,
    private val notifications: RenewalNotificationRepository,
    private val customers: CustomerRepository,
    private val renewals: RenewalService,
    private val client: WhatsAppCloudClient,
    private val jdbc: JdbcTemplate,
    private val tx: TransactionTemplate,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun verifySubscription(mode: String?, token: String?): Boolean {
        val expected = properties.whatsapp.verifyToken
        return mode == "subscribe" && expected.isNotBlank() && token != null &&
            MessageDigest.isEqual(expected.toByteArray(), token.toByteArray())
    }

    fun validSignature(body: ByteArray, header: String?): Boolean {
        val secret = properties.whatsapp.appSecret
        if (secret.isBlank() || header == null || !header.startsWith("sha256=")) return false
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(secret.toByteArray(), "HmacSHA256")) }
        val expected = "sha256=" + mac.doFinal(body).joinToString("") { "%02x".format(it) }
        return MessageDigest.isEqual(expected.toByteArray(), header.lowercase().toByteArray())
    }

    /** Procesa la entrega. Devuelve `false` si algún evento falló (Meta reintentará; los ya procesados se omiten). */
    fun handle(body: ByteArray): Boolean {
        val root = objectMapper.readTree(body)
        var ok = true
        for (entry in root.path("entry")) {
            for (change in entry.path("changes")) {
                val value = change.path("value")
                val phoneNumberId = value.path("metadata").path("phone_number_id").asString(null) ?: continue
                val integration = integrations.findByPhoneNumberId(phoneNumberId) ?: continue
                for (status in value.path("statuses")) ok = safely { onStatus(integration.businessId, status) } && ok
                for (message in value.path("messages")) ok = safely { onMessage(integration.businessId, phoneNumberId, message) } && ok
            }
        }
        return ok
    }

    private fun safely(block: () -> Unit): Boolean = try {
        block()
        true
    } catch (ex: Exception) {
        log.error("Error procesando un evento de WhatsApp", ex)
        false
    }

    private fun onStatus(businessId: UUID, status: JsonNode) {
        val messageId = status.path("id").asString(null) ?: return
        val state = status.path("status").asString("")
        tx.executeWithoutResult {
            if (!firstTime("st:$messageId:$state", businessId, "STATUS")) return@executeWithoutResult
            val n = notifications.findByProviderMessageIdAndBusinessId(messageId, businessId) ?: return@executeWithoutResult
            val at = status.path("timestamp").asString(null)?.toLongOrNull()?.let(Instant::ofEpochSecond) ?: clock.instant()
            when (state) {
                "delivered" -> if (n.deliveryStatus == DeliveryStatus.SENT) {
                    n.deliveryStatus = DeliveryStatus.DELIVERED
                    n.deliveredAt = at
                }
                "read" -> {
                    if (n.deliveryStatus == DeliveryStatus.SENT) n.deliveryStatus = DeliveryStatus.DELIVERED
                    n.deliveredAt = n.deliveredAt ?: at
                    n.readAt = at
                }
                "failed" -> if (n.deliveryStatus == DeliveryStatus.SENT) {
                    // Meta aceptó el envío pero no pudo entregarlo: vuelve a la bandeja manual.
                    n.deliveryStatus = DeliveryStatus.FAILED
                    val error = status.path("errors").path(0)
                    n.failureReason = (error.path("error_data").path("details").asString(null) ?: error.path("title").asString("WhatsApp no pudo entregar el mensaje.")).take(480)
                }
            }
        }
    }

    private fun onMessage(businessId: UUID, phoneNumberId: String, message: JsonNode) {
        val messageId = message.path("id").asString(null) ?: return
        val from = message.path("from").asString("")
        val reply: Reply? = tx.execute {
            if (!firstTime("msg:$messageId", businessId, "MESSAGE")) return@execute null
            when (message.path("type").asString("")) {
                "button" -> onButton(businessId, from, message.path("button").path("payload").asString(""))
                "interactive" -> onButton(businessId, from, message.path("interactive").path("button_reply").path("id").asString(""))
                "text" -> onText(businessId, from, message.path("text").path("body").asString(""))
                else -> null
            }
        }
        // La respuesta se envía fuera de la transacción: estamos dentro de la ventana de 24 h del cliente.
        reply?.let { r ->
            val wa = integrationService.activeByPhoneNumberId(phoneNumberId) ?: return
            runCatching { client.sendText(phoneNumberId, wa.token, r.to, r.text) }
                .onFailure { log.warn("No se pudo responder por WhatsApp: {}", it.message) }
                .onSuccess { id -> if (r.farewell) tx.executeWithoutResult { markFarewellSent(businessId, r.to, id) } }
        }
    }

    /** Mensaje con el que se contesta al cliente; `farewell` indica que es la despedida programada. */
    private data class Reply(val to: String, val text: String, val farewell: Boolean = false)

    private fun onButton(businessId: UUID, from: String, payload: String): Reply? {
        val (notificationId, response) = RenewalDispatchService.parsePayload(payload) ?: return null
        val n = notifications.findById(notificationId).orElse(null)?.takeIf { it.businessId == businessId } ?: return null
        val customer = customers.findById(n.customerId).orElseThrow()
        // Solo el cliente al que se envió el aviso puede responderlo.
        if (customer.phone.filter(Char::isDigit) != from.filter(Char::isDigit)) return null
        val r: RenewalRequest = renewals.respondViaWhatsApp(businessId, n.renewalRequestId, response)
            ?: return Reply(customer.phone, "Esta solicitud de renovación ya no admite cambios. Si necesitas ayuda, escribe al vendedor.")
        val first = customer.fullName.trim().split(Regex("\\s+")).first()
        return when (response) {
            RenewalResponse.ACCEPTED -> Reply(
                customer.phone,
                "¡Gracias, $first! Para completar tu renovación elige Yape o Plin aquí:\n${renewals.link(r.id)}\n" +
                    "Tu renovación se confirma cuando el vendedor verifique tu pago.",
            )
            else -> Reply(customer.phone, RenewalMessages.farewell(customer.fullName, businessNameOf(businessId)), farewell = true)
        }
    }

    private fun onText(businessId: UUID, from: String, body: String): Reply? {
        if (body.trim().uppercase() !in OPT_OUT_WORDS) return null
        val customer = customers.findByBusinessIdAndPhone(businessId, "+" + from.filter(Char::isDigit)) ?: return null
        renewals.setOptOut(businessId, customer.id, true, "CUSTOMER", actorId = null)
        return Reply(customer.phone, "Listo. No volverás a recibir avisos de renovación de este negocio.")
    }

    /** Si la respuesta fue la despedida, el aviso de despedida programado queda como enviado por la API. */
    private fun markFarewellSent(businessId: UUID, to: String, providerMessageId: String) {
        val customer = customers.findByBusinessIdAndPhone(businessId, to) ?: return
        jdbc.query(
            """
            select n.id from renewal_notifications n
            where n.business_id = ? and n.customer_id = ? and n.notification_type = ? and n.delivery_status = 'PENDING'
            order by n.created_at desc limit 1
            """.trimIndent(),
            { rs, _ -> rs.getObject(1, UUID::class.java) }, businessId, customer.id, NotificationType.FAREWELL.name,
        ).firstOrNull()?.let { id ->
            notifications.findById(id).ifPresent {
                it.channel = NotificationChannel.WHATSAPP_API
                it.deliveryStatus = DeliveryStatus.SENT
                it.sentAt = clock.instant()
                it.providerMessageId = providerMessageId
            }
        }
    }

    private fun businessNameOf(businessId: UUID): String =
        jdbc.queryForObject("select business_name from businesses where id = ?", String::class.java, businessId)!!

    private fun firstTime(key: String, businessId: UUID, kind: String): Boolean = jdbc.update(
        "insert into whatsapp_webhook_events (event_key, business_id, kind) values (?, ?, ?) on conflict (event_key) do nothing",
        key.take(200), businessId, kind,
    ) == 1

    private companion object {
        val OPT_OUT_WORDS = setOf("BAJA", "STOP", "NO MOLESTAR", "DARME DE BAJA")
    }
}
