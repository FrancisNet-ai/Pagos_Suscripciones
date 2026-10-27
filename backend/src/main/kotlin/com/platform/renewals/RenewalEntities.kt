package com.platform.renewals

import com.platform.common.persistence.AbstractEntity
import com.platform.common.persistence.VersionedEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class RenewalResponse { NONE, ACCEPTED, DECLINED }

/**
 * OPEN: avisada, sin respuesta. AWAITING_PAYMENT: el cliente dijo que sí (todavía no pagó).
 * COMPLETED: pago verificado y vigencia ampliada. DECLINED: dijo que no. EXPIRED: sin respuesta ni pago.
 */
enum class RenewalStatus { OPEN, AWAITING_PAYMENT, COMPLETED, DECLINED, EXPIRED }

@Entity
@Table(name = "renewal_requests")
class RenewalRequest(
    @Column(nullable = false, updatable = false) val businessId: UUID,
    @Column(nullable = false, updatable = false) val subscriptionId: UUID,
    @Column(nullable = false, updatable = false) val customerId: UUID,
    @Column(nullable = false, updatable = false) val productId: UUID,
    @Column(nullable = false, updatable = false) val cycleExpiresAt: Instant,
    @Column(nullable = false) var tokenHash: String,
    @Column(nullable = false) var tokenExpiresAt: Instant,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var response: RenewalResponse = RenewalResponse.NONE,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var status: RenewalStatus = RenewalStatus.OPEN,
    var respondedAt: Instant? = null,
    var completedAt: Instant? = null,
    var orderId: UUID? = null,
) : VersionedEntity()

enum class RenewalBucket(val statuses: Set<RenewalStatus>) {
    UPCOMING(setOf(RenewalStatus.OPEN)),
    ACCEPTED(setOf(RenewalStatus.AWAITING_PAYMENT)),
    DECLINED(setOf(RenewalStatus.DECLINED)),
    NO_RESPONSE(setOf(RenewalStatus.EXPIRED)),
    COMPLETED(setOf(RenewalStatus.COMPLETED)),
}

interface RenewalRequestRepository : JpaRepository<RenewalRequest, UUID> {
    fun findByTokenHash(tokenHash: String): RenewalRequest?
    fun findByIdAndBusinessId(id: UUID, businessId: UUID): RenewalRequest?
    fun findBySubscriptionIdOrderByCreatedAtDesc(subscriptionId: UUID): List<RenewalRequest>
    /** Bloquea la solicitud: responder y crear el pedido de renovación se serializan. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RenewalRequest r where r.id = :id")
    fun lockById(id: UUID): RenewalRequest?

    fun existsBySubscriptionIdAndCycleExpiresAt(subscriptionId: UUID, cycleExpiresAt: Instant): Boolean

    fun findByBusinessIdAndStatusInOrderByCycleExpiresAtAsc(businessId: UUID, statuses: Collection<RenewalStatus>, pageable: Pageable): Page<RenewalRequest>

    fun countByBusinessIdAndStatus(businessId: UUID, status: RenewalStatus): Long

    /** Solicitudes abiertas o aceptadas cuyo plazo de gracia terminó sin pago. */
    @Query(
        value = """
            select r.id from renewal_requests r join businesses b on b.id = r.business_id
            where r.status in ('OPEN', 'AWAITING_PAYMENT')
              and r.cycle_expires_at + make_interval(days => b.renewal_grace_days) < cast(:now as timestamptz)
            order by r.cycle_expires_at limit :limit
        """,
        nativeQuery = true,
    )
    fun findStale(now: Instant, limit: Int): List<UUID>
}

enum class NotificationType { REMINDER_BEFORE, REMINDER_DUE_DAY, FAREWELL }

enum class NotificationChannel { MANUAL_LINK, WHATSAPP_API }

/**
 * PENDING: listo para enviar. SENT_MANUALLY: el vendedor indicó que lo envió desde su WhatsApp.
 * SENT / DELIVERED / FAILED: reservados para la integración oficial. SKIPPED: no se envía.
 */
enum class DeliveryStatus { PENDING, SENT_MANUALLY, SENT, DELIVERED, FAILED, SKIPPED }

@Entity
@Table(name = "renewal_notifications")
class RenewalNotification(
    @Column(nullable = false, updatable = false) val businessId: UUID,
    @Column(nullable = false, updatable = false) val renewalRequestId: UUID,
    @Column(nullable = false, updatable = false) val customerId: UUID,
    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false) val notificationType: NotificationType,
    @Column(nullable = false) var scheduledAt: Instant,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var channel: NotificationChannel = NotificationChannel.MANUAL_LINK,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var deliveryStatus: DeliveryStatus = DeliveryStatus.PENDING,
    var sentAt: Instant? = null,
    var sentBy: UUID? = null,
    var providerMessageId: String? = null,
) : VersionedEntity()

interface RenewalNotificationRepository : JpaRepository<RenewalNotification, UUID> {
    fun findByIdAndBusinessId(id: UUID, businessId: UUID): RenewalNotification?
    fun findByRenewalRequestId(requestId: UUID): List<RenewalNotification>
    fun findByBusinessIdAndDeliveryStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
        businessId: UUID, status: DeliveryStatus, before: Instant, pageable: Pageable,
    ): Page<RenewalNotification>

    fun countByBusinessIdAndDeliveryStatusAndScheduledAtLessThanEqual(businessId: UUID, status: DeliveryStatus, before: Instant): Long
    fun existsByRenewalRequestIdAndNotificationType(requestId: UUID, type: NotificationType): Boolean
}

@Entity
@Table(name = "subscription_renewals")
class SubscriptionRenewal(
    @Column(nullable = false, updatable = false) val businessId: UUID,
    @Column(nullable = false, updatable = false) val subscriptionId: UUID,
    @Column(nullable = false, updatable = false) val orderId: UUID,
    @Column(updatable = false) val renewalRequestId: UUID?,
    @Column(nullable = false, updatable = false) val previousExpiresAt: Instant,
    @Column(nullable = false, updatable = false) val newExpiresAt: Instant,
    @Column(nullable = false, updatable = false) val amount: BigDecimal,
    @Column(nullable = false, updatable = false) val createdAt: Instant,
) : AbstractEntity()

interface SubscriptionRenewalRepository : JpaRepository<SubscriptionRenewal, UUID> {
    fun findBySubscriptionIdOrderByCreatedAtDesc(subscriptionId: UUID): List<SubscriptionRenewal>
}
