package com.nuvyra.whatsapp

import com.nuvyra.businesses.BusinessRepository
import com.nuvyra.common.audit.AuditActions
import com.nuvyra.common.audit.AuditRecordedEvent
import com.nuvyra.common.config.AppProperties
import com.nuvyra.common.persistence.VersionedEntity
import com.nuvyra.orders.OrderRepository
import com.nuvyra.payments.CustomerPaymentRepository
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.LockModeType
import jakarta.persistence.Table
import org.slf4j.LoggerFactory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** PAYMENT_RECEIVED: el comprador reportó su pago. PAYMENT_VERIFIED: el vendedor lo verificó. */
enum class OrderNotificationKind { PAYMENT_RECEIVED, PAYMENT_VERIFIED }

enum class OrderNotificationStatus { PENDING, SENT, DELIVERED, FAILED }

@Entity
@Table(name = "order_notifications")
class OrderNotification(
    @Column(nullable = false, updatable = false) val businessId: UUID,
    @Column(nullable = false, updatable = false) val orderId: UUID,
    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false) val kind: OrderNotificationKind,
    @Column(nullable = false) var scheduledAt: Instant,
) : VersionedEntity() {
    @Enumerated(EnumType.STRING) @Column(nullable = false) var status: OrderNotificationStatus = OrderNotificationStatus.PENDING
    @Column(nullable = false) var attempts: Int = 0
    var sentAt: Instant? = null
    var deliveredAt: Instant? = null
    var readAt: Instant? = null
    var providerMessageId: String? = null
    var failureReason: String? = null
}

interface OrderNotificationRepository : JpaRepository<OrderNotification, UUID> {
    fun findByOrderIdOrderByCreatedAtAsc(orderId: UUID): List<OrderNotification>
    fun findByProviderMessageIdAndBusinessId(providerMessageId: String, businessId: UUID): OrderNotification?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from OrderNotification n where n.id = :id")
    fun lockById(id: UUID): OrderNotification?
}

/**
 * Confirmaciones transaccionales al comprador por la integración oficial de WhatsApp. Se encolan en la
 * misma transacción que la acción (reporte o verificación del pago) y un proceso las envía después:
 * nada se envía si el comprador no lo autorizó al comprar o si la tienda no tiene la plantilla.
 */
@Service
class OrderNotificationService(
    private val repository: OrderNotificationRepository,
    private val integrations: WhatsAppIntegrationRepository,
    private val whatsApp: WhatsAppIntegrationService,
    private val client: WhatsAppCloudClient,
    private val orders: OrderRepository,
    private val payments: CustomerPaymentRepository,
    private val businesses: BusinessRepository,
    private val jdbc: JdbcTemplate,
    private val properties: AppProperties,
    private val clock: Clock,
) {
    /** La tienda tiene la integración activa con al menos una plantilla de confirmación. */
    @Transactional(readOnly = true)
    fun availableFor(businessId: UUID): Boolean {
        val i = integrations.findById(businessId).orElse(null) ?: return false
        return (i.orderTemplate != null || i.paymentTemplate != null) && whatsApp.isActive(businessId)
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun enqueueFromAudit(event: AuditRecordedEvent) {
        val kind = when (event.entry.action) {
            AuditActions.PAYMENT_REPORTED -> OrderNotificationKind.PAYMENT_RECEIVED
            AuditActions.PAYMENT_VERIFIED -> OrderNotificationKind.PAYMENT_VERIFIED
            else -> return
        }
        val businessId = event.entry.businessId ?: return
        val payment = event.entry.entityId?.let { payments.findById(it).orElse(null) } ?: return
        if (!consented(payment.orderId) || templateFor(businessId, kind) == null) return
        jdbc.update(
            "insert into order_notifications (business_id, order_id, kind, scheduled_at) values (?, ?, ?, ?) on conflict (order_id, kind) do nothing",
            businessId, payment.orderId, kind.name, Timestamp.from(clock.instant()),
        )
    }

    fun dueForDispatch(limit: Int): List<UUID> = jdbc.queryForList(
        "select id from order_notifications where status = 'PENDING' and scheduled_at <= ? and attempts < ? order by scheduled_at limit ?",
        UUID::class.java, Timestamp.from(clock.instant()), properties.whatsapp.maxAttempts, limit,
    ).filterNotNull()

    /** Envía una confirmación. Un error definitivo la deja FAILED; uno temporal se reintenta con espera. */
    @Transactional
    fun dispatch(id: UUID): Boolean {
        val n = repository.lockById(id)?.takeIf { it.status == OrderNotificationStatus.PENDING } ?: return false
        val template = templateFor(n.businessId, n.kind)
        val wa = whatsApp.active(n.businessId)
        if (template == null || wa == null || !consented(n.orderId)) {
            n.status = OrderNotificationStatus.FAILED
            n.failureReason = "La tienda desactivó la confirmación o el comprador retiró su autorización."
            return true
        }
        val order = orders.findById(n.orderId).orElseThrow()
        val business = businesses.findById(n.businessId).orElseThrow()
        val first = order.customerNameSnapshot.trim().split(Regex("\\s+")).first()
        n.attempts += 1
        try {
            n.providerMessageId = client.sendTemplate(
                wa.integration.phoneNumberId, wa.token, order.customerPhoneSnapshot, template, wa.integration.templateLanguage,
                bodyParams = listOf(first, order.orderNumber, OrderMessages.formatSoles(order.totalAmount), business.businessName),
            )
            n.status = OrderNotificationStatus.SENT
            n.sentAt = clock.instant()
            n.failureReason = null
        } catch (ex: WhatsAppApiException) {
            n.failureReason = ex.message?.take(480)
            if (!ex.retryable || n.attempts >= properties.whatsapp.maxAttempts) n.status = OrderNotificationStatus.FAILED
            else n.scheduledAt = clock.instant().plus(Duration.ofMinutes(5L * n.attempts))
            if (ex.code == INVALID_TOKEN) whatsApp.markBroken(n.businessId, "Meta rechazó el token de acceso: ${ex.message}")
            log.warn("Confirmación {} no enviada por WhatsApp: {}", n.id, ex.message)
        }
        return true
    }

    /** Estado de entrega informado por el webhook de Meta. Devuelve `false` si el mensaje no es de un pedido. */
    @Transactional
    fun onStatus(businessId: UUID, messageId: String, state: String, at: Instant, failure: String?): Boolean {
        val n = repository.findByProviderMessageIdAndBusinessId(messageId, businessId) ?: return false
        when (state) {
            "delivered" -> if (n.status == OrderNotificationStatus.SENT) {
                n.status = OrderNotificationStatus.DELIVERED
                n.deliveredAt = at
            }
            "read" -> {
                if (n.status == OrderNotificationStatus.SENT) n.status = OrderNotificationStatus.DELIVERED
                n.deliveredAt = n.deliveredAt ?: at
                n.readAt = at
            }
            "failed" -> if (n.status == OrderNotificationStatus.SENT) {
                n.status = OrderNotificationStatus.FAILED
                n.failureReason = failure?.take(480)
            }
        }
        return true
    }

    @Transactional(readOnly = true)
    fun forOrder(orderId: UUID): List<OrderNotification> = repository.findByOrderIdOrderByCreatedAtAsc(orderId)

    private fun templateFor(businessId: UUID, kind: OrderNotificationKind): String? {
        val i = integrations.findById(businessId).orElse(null) ?: return null
        return when (kind) {
            OrderNotificationKind.PAYMENT_RECEIVED -> i.orderTemplate
            OrderNotificationKind.PAYMENT_VERIFIED -> i.paymentTemplate
        }
    }

    /** Última decisión del comprador sobre confirmaciones, registrada al comprar ese pedido. */
    private fun consented(orderId: UUID): Boolean = jdbc.queryForList(
        """
        select c.granted and c.revoked_at is null from customer_consents c
        join orders o on o.customer_id = c.customer_id
        where o.id = ? and c.purpose = 'ORDER_UPDATES' order by c.created_at desc limit 1
        """.trimIndent(),
        Boolean::class.java, orderId,
    ).firstOrNull() == true

    private companion object {
        val log = LoggerFactory.getLogger(OrderNotificationService::class.java)
        const val INVALID_TOKEN = 190
    }
}

@Component
class OrderNotificationJob(private val service: OrderNotificationService) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${app.whatsapp.dispatch-interval:PT2M}", initialDelayString = "\${app.whatsapp.dispatch-initial-delay:PT1M}")
    fun run() {
        var sent = 0
        while (true) {
            val ids = service.dueForDispatch(100)
            if (ids.isEmpty()) break
            val processed = ids.count { runCatching { service.dispatch(it) }.getOrDefault(false) }
            sent += processed
            if (ids.size < 100 || processed == 0) break
        }
        if (sent > 0) log.info("Confirmaciones de pedido procesadas por WhatsApp: {}", sent)
    }
}
