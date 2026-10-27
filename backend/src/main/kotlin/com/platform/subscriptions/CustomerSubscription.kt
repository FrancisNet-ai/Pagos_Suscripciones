package com.platform.subscriptions

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

enum class SubscriptionOrigin { ORDER, MANUAL, IMPORT }

/**
 * PENDING_ACTIVATION: pagada, el vendedor aún no entrega el servicio. ACTIVE: vigente.
 * RENEWAL_PENDING: (Fase 5) en proceso de renovación. EXPIRED: terminó el periodo pagado.
 * CANCELLED: anulada por el vendedor. ENDED: vencida y con el acceso efectivamente retirado.
 */
enum class SubscriptionStatus { PENDING_ACTIVATION, ACTIVE, RENEWAL_PENDING, EXPIRED, CANCELLED, ENDED }

@Entity
@Table(name = "customer_subscriptions")
class CustomerSubscription(
    @Column(nullable = false, updatable = false) val businessId: UUID,
    @Column(nullable = false, updatable = false) val customerId: UUID,
    @Column(nullable = false, updatable = false) val productId: UUID,
    @Column(updatable = false) val orderId: UUID?,
    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false) val origin: SubscriptionOrigin,
    @Column(nullable = false, updatable = false) val productNameSnapshot: String,
    @Column(nullable = false) var durationDays: Int,
    @Column(nullable = false) var contractedPrice: BigDecimal,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var status: SubscriptionStatus,
    /** Momento en que se verificó el pago (no aplica a suscripciones antiguas registradas a mano). */
    var paidAt: Instant? = null,
    /** Inicio real del servicio: lo registra el vendedor, puede diferir del pago. */
    var startAt: Instant? = null,
    var expiresAt: Instant? = null,
    var lastRenewedAt: Instant? = null,
    var cancelledAt: Instant? = null,
    var cancellationReason: String? = null,
    var notes: String? = null,
) : VersionedEntity()

/** RENEWAL = en proceso de renovación; RELEASE_PENDING = terminadas con el cupo aún por liberar. */
enum class SubscriptionFilter { ALL, PENDING_ACTIVATION, ACTIVE, EXPIRING, RENEWAL, EXPIRED, CLOSED, RELEASE_PENDING }

interface CustomerSubscriptionRepository : JpaRepository<CustomerSubscription, UUID> {
    fun findByIdAndBusinessId(id: UUID, businessId: UUID): CustomerSubscription?
    fun findByCustomerIdAndBusinessIdOrderByCreatedAtDesc(customerId: UUID, businessId: UUID): List<CustomerSubscription>
    fun findByOrderIdAndBusinessId(orderId: UUID, businessId: UUID): CustomerSubscription?
    fun findByCustomerIdIn(customerIds: Collection<UUID>): List<CustomerSubscription>

    @Query(
        """
        select count(s) > 0 from CustomerSubscription s
        where s.businessId = :businessId and s.customerId = :customerId and s.productId = :productId
          and s.startAt = :startAt and s.origin <> com.platform.subscriptions.SubscriptionOrigin.ORDER
          and s.status <> com.platform.subscriptions.SubscriptionStatus.CANCELLED
        """,
    )
    fun duplicateManual(businessId: UUID, customerId: UUID, productId: UUID, startAt: Instant): Boolean

    @Query(
        """
        select s from CustomerSubscription s, com.platform.customers.Customer c
        where c.id = s.customerId and s.businessId = :businessId
          and s.status in :statuses
          and (:expiringOnly = false or s.expiresAt < :expiringBefore)
          and (:releasePendingOnly = false or exists (select 1 from com.platform.capacity.CapacityAllocation a
               where a.customerSubscriptionId = s.id and a.allocationStatus = com.platform.capacity.AllocationStatus.RELEASE_PENDING))
          and (lower(c.fullName) like :pattern escape '\' or c.phone like :pattern escape '\' or lower(s.productNameSnapshot) like :pattern escape '\')
        """,
    )
    fun search(
        businessId: UUID,
        statuses: Collection<SubscriptionStatus>,
        expiringOnly: Boolean,
        expiringBefore: Instant,
        releasePendingOnly: Boolean,
        pattern: String,
        pageable: Pageable,
    ): Page<CustomerSubscription>

    @Query(value = "select subscription_id from renewal_requests where id = :renewalRequestId", nativeQuery = true)
    fun subscriptionIdForRenewal(renewalRequestId: UUID): UUID?

    /** Activas cuyo periodo terminó (tarea de vencimiento). */
    @Query("select s.id from CustomerSubscription s where s.status = 'ACTIVE' and s.expiresAt < :now order by s.expiresAt")
    fun findOverdue(now: Instant, pageable: Pageable): List<UUID>
}
