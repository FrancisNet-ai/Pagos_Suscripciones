package com.nuvyra.renewals

import com.nuvyra.businesses.BusinessRepository
import com.nuvyra.common.config.AppProperties
import com.nuvyra.customers.CustomerRepository
import com.nuvyra.subscriptions.CustomerSubscriptionRepository
import com.nuvyra.whatsapp.TemplateButton
import com.nuvyra.whatsapp.WhatsAppApiException
import com.nuvyra.whatsapp.WhatsAppCloudClient
import com.nuvyra.whatsapp.WhatsAppIntegrationService
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Clock
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/**
 * Envío automático de avisos de renovación por WhatsApp Business Platform. Solo se envía si:
 * el negocio conectó y verificó su número, su plan incluye avisos automáticos, el cliente autorizó
 * recordatorios y no pidió dejar de recibirlos. El resto de avisos sigue en el flujo manual.
 */
@Service
class RenewalDispatchService(
    private val jdbc: JdbcTemplate,
    private val notifications: RenewalNotificationRepository,
    private val requests: RenewalRequestRepository,
    private val subscriptions: CustomerSubscriptionRepository,
    private val customers: CustomerRepository,
    private val businesses: BusinessRepository,
    private val integrations: WhatsAppIntegrationService,
    private val client: WhatsAppCloudClient,
    private val tokens: RenewalTokens,
    private val properties: AppProperties,
    private val clock: Clock,
) {
    /** Avisos vencidos de negocios con la integración activa y clientes que autorizaron recordatorios. */
    fun dueForDispatch(limit: Int): List<UUID> = jdbc.queryForList(
        """
        select n.id from renewal_notifications n
        join whatsapp_integrations w on w.business_id = n.business_id and w.enabled and w.status = 'VERIFIED'
        where n.delivery_status = 'PENDING' and n.scheduled_at <= cast(? as timestamptz) and n.attempts < ?
          and (n.notification_type <> 'FAREWELL' or w.farewell_template is not null)
          and not exists (select 1 from notification_preferences p where p.customer_id = n.customer_id and p.opted_out)
          and exists (
            select 1 from customer_consents c
            where c.customer_id = n.customer_id and c.purpose = 'RENEWAL_REMINDERS' and c.granted and c.revoked_at is null
              and not exists (select 1 from customer_consents c2 where c2.customer_id = c.customer_id and c2.purpose = c.purpose and c2.created_at > c.created_at)
          )
        order by n.scheduled_at limit ?
        """.trimIndent(),
        UUID::class.java, Timestamp.from(clock.instant()), properties.whatsapp.maxAttempts, limit,
    ).filterNotNull()

    /**
     * Envía un aviso. El aviso queda bloqueado durante el envío, así dos ejecuciones no lo duplican.
     * Un error definitivo lo marca FAILED (el vendedor puede enviarlo a mano); uno temporal se reintenta.
     */
    @Transactional
    fun dispatch(notificationId: UUID): Boolean {
        val n = notifications.lockById(notificationId)?.takeIf { it.deliveryStatus == DeliveryStatus.PENDING } ?: return false
        val wa = integrations.active(n.businessId) ?: return false
        val r = requests.findById(n.renewalRequestId).orElseThrow()
        val s = subscriptions.findById(r.subscriptionId).orElseThrow()
        val c = customers.findById(n.customerId).orElseThrow()
        val business = businesses.findById(n.businessId).orElseThrow()
        val zone = ZoneId.of(business.timeZone)
        val first = c.fullName.trim().split(Regex("\\s+")).first()
        n.attempts += 1
        try {
            val id = if (n.notificationType == NotificationType.FAREWELL) {
                client.sendTemplate(wa.integration.phoneNumberId, wa.token, c.phone, wa.integration.farewellTemplate!!, wa.integration.templateLanguage, listOf(first, business.businessName))
            } else {
                val dueToday = r.cycleExpiresAt.atZone(zone).toLocalDate() == clock.instant().atZone(zone).toLocalDate()
                val whenText = if (dueToday) "hoy" else "el ${DATE.format(r.cycleExpiresAt.atZone(zone))}"
                client.sendTemplate(
                    wa.integration.phoneNumberId, wa.token, c.phone, wa.integration.reminderTemplate, wa.integration.templateLanguage,
                    bodyParams = listOf(first, s.productNameSnapshot, business.businessName, whenText),
                    buttons = listOf(
                        TemplateButton.QuickReply(0, payload(n.id, accept = true)),
                        TemplateButton.QuickReply(1, payload(n.id, accept = false)),
                        TemplateButton.UrlSuffix(2, tokens.tokenFor(r.id)),
                    ),
                )
            }
            n.channel = NotificationChannel.WHATSAPP_API
            n.deliveryStatus = DeliveryStatus.SENT
            n.sentAt = clock.instant()
            n.providerMessageId = id
            n.failureReason = null
        } catch (ex: WhatsAppApiException) {
            n.failureReason = ex.message?.take(480)
            if (!ex.retryable || n.attempts >= properties.whatsapp.maxAttempts) {
                n.deliveryStatus = DeliveryStatus.FAILED
            } else {
                // Fallo temporal: se reintenta más tarde con espera creciente.
                n.scheduledAt = clock.instant().plus(java.time.Duration.ofMinutes(5L * n.attempts))
            }
            // Token inválido o revocado: se desactiva la integración para no fallar con cada aviso.
            if (ex.code == INVALID_TOKEN) integrations.markBroken(n.businessId, "Meta rechazó el token de acceso: ${ex.message}")
            log.warn("Aviso {} no enviado por WhatsApp: {}", n.id, ex.message)
        }
        return true
    }

    companion object {
        private val log = LoggerFactory.getLogger(RenewalDispatchService::class.java)
        private val DATE = DateTimeFormatter.ofPattern("d 'de' MMMM", Locale.forLanguageTag("es-PE"))
        private const val INVALID_TOKEN = 190
        private const val PREFIX = "RNW"

        fun payload(notificationId: UUID, accept: Boolean) = "$PREFIX:$notificationId:${if (accept) "Y" else "N"}"

        /** Interpreta el payload de un botón de respuesta rápida. */
        fun parsePayload(payload: String): Pair<UUID, RenewalResponse>? {
            val parts = payload.split(':')
            if (parts.size != 3 || parts[0] != PREFIX) return null
            val id = runCatching { UUID.fromString(parts[1]) }.getOrNull() ?: return null
            return when (parts[2]) {
                "Y" -> id to RenewalResponse.ACCEPTED
                "N" -> id to RenewalResponse.DECLINED
                else -> null
            }
        }
    }
}

@Component
class RenewalDispatchJob(private val dispatcher: RenewalDispatchService) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${app.whatsapp.dispatch-interval:PT2M}", initialDelayString = "\${app.whatsapp.dispatch-initial-delay:PT1M}")
    fun run() {
        var sent = 0
        while (true) {
            val ids = dispatcher.dueForDispatch(100)
            if (ids.isEmpty()) break
            val processed = ids.count { runCatching { dispatcher.dispatch(it) }.getOrDefault(false) }
            sent += processed
            if (ids.size < 100 || processed == 0) break
        }
        if (sent > 0) log.info("Avisos de renovación procesados por WhatsApp: {}", sent)
    }
}
