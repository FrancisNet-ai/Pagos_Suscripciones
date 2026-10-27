package com.platform.saas

import com.platform.common.persistence.VersionedEntity
import com.platform.payments.PaymentMethodType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.LockModeType
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.UpdateTimestamp
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.math.BigDecimal
import java.time.Instant
import java.time.Period
import java.util.UUID

enum class BillingPeriod(val label: String, val period: Period) {
    MONTHLY("Mensual", Period.ofMonths(1)),
    QUARTERLY("Trimestral", Period.ofMonths(3)),
    YEARLY("Anual", Period.ofYears(1)),
}

/** Funciones que un plan puede incluir o no. */
enum class PlanFeature(val label: String) {
    REPORTS("Reportes"),
    REMINDERS("Avisos de renovación automáticos"),
    BRANDING("Personalización de la tienda"),
}

@Entity
@Table(name = "saas_plans")
class SaasPlan(
    @Column(nullable = false) var name: String,
    var description: String? = null,
    @Column(nullable = false) var price: BigDecimal,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var billingPeriod: BillingPeriod,
    var productLimit: Int? = null,
    var customerLimit: Int? = null,
    @Column(nullable = false) var adminUserLimit: Int = 1,
    @Column(nullable = false) var featureReports: Boolean = true,
    @Column(nullable = false) var featureReminders: Boolean = true,
    @Column(nullable = false) var featureBranding: Boolean = true,
    @Column(name = "is_active", nullable = false) var active: Boolean = true,
    @Column(nullable = false) var sortOrder: Int = 0,
) : VersionedEntity() {
    fun has(feature: PlanFeature) = when (feature) {
        PlanFeature.REPORTS -> featureReports
        PlanFeature.REMINDERS -> featureReminders
        PlanFeature.BRANDING -> featureBranding
    }

    val features: Set<PlanFeature> get() = PlanFeature.entries.filter(::has).toSet()
}

interface SaasPlanRepository : JpaRepository<SaasPlan, UUID> {
    fun findAllByOrderBySortOrderAscPriceAsc(): List<SaasPlan>
    fun findByActiveTrueOrderBySortOrderAscPriceAsc(): List<SaasPlan>
    fun existsByNameIgnoreCase(name: String): Boolean
    fun existsByNameIgnoreCaseAndIdNot(name: String, id: UUID): Boolean
}

/**
 * PENDING_PAYMENT: eligió un plan y aún no tiene un periodo pagado. ACTIVE: vigente. PAST_DUE: venció
 * y está dentro del plazo de gracia. EXPIRED: sin vigencia. CANCELLED: retiró su solicitud sin pagar.
 */
enum class SaasSubscriptionStatus { PENDING_PAYMENT, ACTIVE, PAST_DUE, CANCELLED, EXPIRED }

@Entity
@Table(name = "saas_subscriptions")
class SaasSubscription(
    @Column(nullable = false, updatable = false) val businessId: UUID,
    @Column(nullable = false) var planId: UUID,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var status: SaasSubscriptionStatus = SaasSubscriptionStatus.PENDING_PAYMENT,
    var startAt: Instant? = null,
    var expiresAt: Instant? = null,
    var graceUntil: Instant? = null,
    var cancelledAt: Instant? = null,
) : VersionedEntity()

interface SaasSubscriptionRepository : JpaRepository<SaasSubscription, UUID> {
    fun findByBusinessId(businessId: UUID): SaasSubscription?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SaasSubscription s where s.businessId = :businessId")
    fun lockByBusinessId(businessId: UUID): SaasSubscription?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SaasSubscription s where s.id = :id")
    fun lockById(id: UUID): SaasSubscription?

    @Query("select s.id from SaasSubscription s where s.status = com.platform.saas.SaasSubscriptionStatus.ACTIVE and s.expiresAt <= :now order by s.expiresAt")
    fun findLapsed(now: Instant, pageable: Pageable): List<UUID>

    @Query("select s.id from SaasSubscription s where s.status = com.platform.saas.SaasSubscriptionStatus.PAST_DUE and s.graceUntil <= :now order by s.graceUntil")
    fun findGraceEnded(now: Instant, pageable: Pageable): List<UUID>
}

enum class InvoiceKind(val label: String) { NEW("Alta"), RENEWAL("Renovación"), PLAN_CHANGE("Cambio de plan") }

enum class InvoiceStatus { OPEN, PAID, VOID }

@Entity
@Table(name = "saas_invoices")
class SaasInvoice(
    @Column(nullable = false, updatable = false) val invoiceNumber: String,
    @Column(nullable = false, updatable = false) val businessId: UUID,
    @Column(nullable = false, updatable = false) val subscriptionId: UUID,
    @Column(nullable = false, updatable = false) val planId: UUID,
    @Column(nullable = false, updatable = false) val planNameSnapshot: String,
    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false) val billingPeriod: BillingPeriod,
    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false) val kind: InvoiceKind,
    @Column(nullable = false, updatable = false) val amount: BigDecimal,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var status: InvoiceStatus = InvoiceStatus.OPEN,
    var periodStart: Instant? = null,
    var periodEnd: Instant? = null,
    var paidAt: Instant? = null,
    var voidedAt: Instant? = null,
) : VersionedEntity()

interface SaasInvoiceRepository : JpaRepository<SaasInvoice, UUID> {
    fun findByIdAndBusinessId(id: UUID, businessId: UUID): SaasInvoice?
    fun findFirstByBusinessIdAndStatus(businessId: UUID, status: InvoiceStatus): SaasInvoice?
    fun findByBusinessIdOrderByCreatedAtDesc(businessId: UUID, pageable: Pageable): List<SaasInvoice>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from SaasInvoice i where i.id = :id")
    fun lockById(id: UUID): SaasInvoice?
}

enum class SaasPaymentStatus { PENDING_VERIFICATION, VERIFIED, REJECTED }

@Entity
@Table(name = "saas_payments")
class SaasPayment(
    @Column(nullable = false, updatable = false) val businessId: UUID,
    @Column(nullable = false, updatable = false) val invoiceId: UUID,
    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false) val method: PaymentMethodType,
    @Column(nullable = false, updatable = false) val receiverHolderSnapshot: String,
    @Column(nullable = false, updatable = false) val receiverPhoneSnapshot: String,
    @Column(nullable = false, updatable = false) val amount: BigDecimal,
    @Column(nullable = false, updatable = false) val transactionReference: String,
    @Column(updatable = false) val receiptKey: String?,
    @Column(nullable = false, updatable = false) val reportedAt: Instant,
    @Column(nullable = false, updatable = false) val reportedBy: UUID,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var status: SaasPaymentStatus = SaasPaymentStatus.PENDING_VERIFICATION,
    var verifiedAt: Instant? = null,
    var verifiedBy: UUID? = null,
    var rejectedAt: Instant? = null,
    var rejectionReason: String? = null,
) : VersionedEntity()

interface SaasPaymentRepository : JpaRepository<SaasPayment, UUID> {
    fun findByInvoiceIdOrderByReportedAtDesc(invoiceId: UUID): List<SaasPayment>
    fun findByBusinessIdOrderByReportedAtDesc(businessId: UUID, pageable: Pageable): List<SaasPayment>
    fun findByStatusOrderByReportedAtAsc(status: SaasPaymentStatus, pageable: Pageable): Page<SaasPayment>
    fun findAllByOrderByReportedAtDesc(pageable: Pageable): Page<SaasPayment>
    fun existsByMethodAndTransactionReferenceAndStatusIn(method: PaymentMethodType, reference: String, statuses: Collection<SaasPaymentStatus>): Boolean

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from SaasPayment p where p.id = :id")
    fun lockById(id: UUID): SaasPayment?
}

@Entity
@Table(name = "platform_settings")
class PlatformSettings(
    @Id val id: Short = 1,
    var supportWhatsapp: String? = null,
    var supportEmail: String? = null,
    @Column(nullable = false) var saasGraceDays: Int = 5,
    @Version @Column(nullable = false) var version: Long = 0,
    @UpdateTimestamp @Column(nullable = false) var updatedAt: Instant? = null,
)

interface PlatformSettingsRepository : JpaRepository<PlatformSettings, Short>

@Entity
@Table(name = "platform_payment_methods")
class PlatformPaymentMethod(
    @Id @Enumerated(EnumType.STRING) val method: PaymentMethodType,
    var holderName: String? = null,
    var phone: String? = null,
    var qrImageUrl: String? = null,
    @Column(name = "is_enabled", nullable = false) var enabled: Boolean = false,
    @Version @Column(nullable = false) var version: Long = 0,
    @UpdateTimestamp @Column(nullable = false) var updatedAt: Instant? = null,
)

interface PlatformPaymentMethodRepository : JpaRepository<PlatformPaymentMethod, PaymentMethodType> {
    fun findAllByOrderByMethodAsc(): List<PlatformPaymentMethod>
}
