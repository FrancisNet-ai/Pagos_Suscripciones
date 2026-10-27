package com.platform.saas

import com.platform.businesses.BusinessRepository
import com.platform.common.audit.AuditActions
import com.platform.common.audit.AuditService
import com.platform.common.error.ApiException
import com.platform.common.ratelimit.RateLimiter
import com.platform.media.MediaStorage
import com.platform.payments.PaymentMethodType
import com.platform.whatsapp.ManualWhatsAppLinkProvider
import org.springframework.data.domain.PageRequest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * Facturación SaaS: lo que el VENDEDOR paga a la plataforma. Completamente separada de las ventas
 * del vendedor (orders / customer_payments). Un pago reportado nunca activa el plan por sí solo:
 * el SUPER_ADMIN confirma la recepción real del dinero.
 */
@Service
class BillingService(
    private val plans: SaasPlanRepository,
    private val subscriptions: SaasSubscriptionRepository,
    private val invoices: SaasInvoiceRepository,
    private val payments: SaasPaymentRepository,
    private val platformMethods: PlatformPaymentMethodRepository,
    private val settings: PlatformSettingsRepository,
    private val businesses: BusinessRepository,
    private val entitlements: EntitlementService,
    private val media: MediaStorage,
    private val rateLimiter: RateLimiter,
    private val audit: AuditService,
    private val jdbc: JdbcTemplate,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun overview(businessId: UUID): BillingOverview {
        val business = businesses.findById(businessId).orElseThrow { ApiException.notFound() }
        val access = entitlements.access(business)
        val sub = subscriptions.findByBusinessId(businessId)
        val usage = entitlements.usage(businessId)
        val plan = access.plan
        return BillingOverview(
            access = AccessSummary.of(access),
            suspensionReason = access.suspensionReason,
            currentPlan = plan?.let { PlanResponse.of(it) },
            startAt = sub?.startAt,
            graceDays = entitlements.graceDays(),
            products = UsageLine(usage.products, plan?.productLimit),
            customers = UsageLine(usage.customers, plan?.customerLimit),
            openInvoice = invoices.findFirstByBusinessIdAndStatus(businessId, InvoiceStatus.OPEN)?.let(::toResponse),
            paymentOptions = paymentOptions(),
            support = support(),
            plans = plans.findByActiveTrueOrderBySortOrderAscPriceAsc().map { PlanResponse.of(it) },
            history = invoices.findByBusinessIdOrderByCreatedAtDesc(businessId, PageRequest.of(0, 24))
                .filter { it.status != InvoiceStatus.OPEN }.map(::toResponse),
        )
    }

    /**
     * Genera (o reutiliza) el cobro del plan elegido. Un plan gratuito se activa al instante porque no
     * hay dinero que verificar. Idempotente: elegir de nuevo el mismo plan devuelve el mismo cobro.
     */
    @Transactional
    fun checkout(businessId: UUID, actorId: UUID, planId: UUID): BillingOverview {
        rateLimiter.check("saas-checkout:$businessId", limit = 20, window = Duration.ofHours(1))
        val plan = plans.findById(planId).orElse(null) ?: throw ApiException.notFound("Este plan no existe.")
        var sub = subscriptions.lockByBusinessId(businessId)
        // Un plan desactivado ya no se ofrece, pero quien lo tiene puede seguir renovándolo.
        if (!plan.active && sub?.planId != plan.id) throw ApiException.conflict("PLAN_UNAVAILABLE", "Este plan ya no está disponible. Elige otro.")

        if (sub == null) {
            sub = subscriptions.saveAndFlush(SaasSubscription(businessId = businessId, planId = plan.id))
        } else if (sub.status == SaasSubscriptionStatus.PENDING_PAYMENT || sub.status == SaasSubscriptionStatus.CANCELLED) {
            sub.planId = plan.id
            sub.status = SaasSubscriptionStatus.PENDING_PAYMENT
            sub.cancelledAt = null
        }

        val open = invoices.findFirstByBusinessIdAndStatus(businessId, InvoiceStatus.OPEN)
        if (open != null) {
            if (open.planId == plan.id) return overview(businessId)
            if (hasPendingPayment(open.id)) {
                throw ApiException.conflict(
                    "PAYMENT_UNDER_REVIEW",
                    "Tienes un pago en revisión para el cobro ${open.invoiceNumber}. Espera la verificación antes de cambiar de plan.",
                )
            }
            void(open)
        }
        val kind = when {
            sub.startAt == null -> InvoiceKind.NEW
            sub.planId == plan.id -> InvoiceKind.RENEWAL
            else -> InvoiceKind.PLAN_CHANGE
        }
        val invoice = invoices.saveAndFlush(
            SaasInvoice(
                invoiceNumber = nextNumber(), businessId = businessId, subscriptionId = sub.id, planId = plan.id,
                planNameSnapshot = plan.name, billingPeriod = plan.billingPeriod, kind = kind, amount = plan.price,
            ),
        )
        audit.record(
            AuditActions.SAAS_CHECKOUT_STARTED, actorId = actorId, businessId = businessId, entityType = "SAAS_INVOICE", entityId = invoice.id,
            metadata = mapOf("invoiceNumber" to invoice.invoiceNumber, "plan" to plan.name, "amount" to plan.price, "kind" to kind.name),
        )
        if (plan.price.signum() == 0) applyPaid(invoice, actorId = null)
        return overview(businessId)
    }

    @Transactional
    fun cancelInvoice(businessId: UUID, actorId: UUID, invoiceId: UUID): BillingOverview {
        val invoice = invoices.findByIdAndBusinessId(invoiceId, businessId) ?: throw ApiException.notFound("El cobro no existe.")
        val locked = invoices.lockById(invoice.id)!!
        if (locked.status != InvoiceStatus.OPEN) throw ApiException.conflict("INVOICE_CLOSED", "Este cobro ya no está pendiente.")
        if (hasPendingPayment(locked.id)) {
            throw ApiException.conflict("PAYMENT_UNDER_REVIEW", "Tu pago está en revisión; no puedes anular este cobro ahora.")
        }
        void(locked)
        val sub = subscriptions.lockById(locked.subscriptionId)!!
        if (sub.status == SaasSubscriptionStatus.PENDING_PAYMENT) {
            sub.status = SaasSubscriptionStatus.CANCELLED
            sub.cancelledAt = clock.instant()
        }
        audit.record(
            AuditActions.SAAS_INVOICE_CANCELLED, actorId = actorId, businessId = businessId, entityType = "SAAS_INVOICE", entityId = locked.id,
            metadata = mapOf("invoiceNumber" to locked.invoiceNumber),
        )
        return overview(businessId)
    }

    /** El vendedor informa que pagó. Queda PENDING_VERIFICATION hasta que el SUPER_ADMIN lo confirme. */
    @Transactional
    fun reportPayment(
        businessId: UUID,
        actorId: UUID,
        invoiceId: UUID,
        method: PaymentMethodType,
        rawReference: String,
        receipt: ByteArray?,
    ): BillingOverview {
        rateLimiter.check("saas-report:$businessId", limit = 10, window = Duration.ofHours(1))
        val invoice = invoices.findByIdAndBusinessId(invoiceId, businessId)?.let { invoices.lockById(it.id) }
            ?: throw ApiException.notFound("El cobro no existe.")
        if (invoice.status != InvoiceStatus.OPEN) throw ApiException.conflict("INVOICE_CLOSED", "Este cobro ya no está pendiente.")
        if (invoice.amount.signum() == 0) throw ApiException.conflict("INVOICE_CLOSED", "Este plan no requiere pago.")
        if (hasPendingPayment(invoice.id)) {
            throw ApiException.conflict("PAYMENT_ALREADY_REPORTED", "Ya reportaste un pago para este cobro. Lo estamos verificando.")
        }
        val receiver = platformMethods.findById(method).orElse(null)?.takeIf { it.enabled }
            ?: throw ApiException.invalidField("method", "Este método de pago no está disponible")
        val reference = rawReference.trim().uppercase()
        if (!REFERENCE.matches(reference)) {
            throw ApiException.invalidField("transactionReference", "Ingresa el código de operación (4 a 40 letras o números)")
        }
        if (payments.existsByMethodAndTransactionReferenceAndStatusIn(method, reference, ACTIVE_PAYMENT_STATUSES)) {
            throw ApiException.conflict("DUPLICATE_REFERENCE", "Este código de operación ya fue registrado. Revisa el código de tu comprobante.")
        }
        val payment = payments.saveAndFlush(
            SaasPayment(
                businessId = businessId, invoiceId = invoice.id, method = method,
                receiverHolderSnapshot = receiver.holderName!!, receiverPhoneSnapshot = receiver.phone!!,
                amount = invoice.amount, transactionReference = reference, receiptKey = receipt?.let { media.storeReceipt(it) },
                reportedAt = clock.instant(), reportedBy = actorId,
            ),
        )
        audit.record(
            AuditActions.SAAS_PAYMENT_REPORTED, actorId = actorId, businessId = businessId, entityType = "SAAS_PAYMENT", entityId = payment.id,
            metadata = mapOf("invoiceNumber" to invoice.invoiceNumber, "method" to method.name, "reference" to reference, "receipt" to (receipt != null)),
        )
        return overview(businessId)
    }

    /** Solo SUPER_ADMIN. Bloquea el pago y transiciona únicamente desde PENDING_VERIFICATION. */
    @Transactional
    fun verify(actorId: UUID, paymentId: UUID) {
        val payment = lockPending(paymentId)
        val invoice = invoices.lockById(payment.invoiceId)!!
        if (invoice.status != InvoiceStatus.OPEN) throw ApiException.conflict("INVOICE_CLOSED", "El cobro de este pago ya no está pendiente.")
        payment.status = SaasPaymentStatus.VERIFIED
        payment.verifiedAt = clock.instant()
        payment.verifiedBy = actorId
        applyPaid(invoice, actorId)
        audit.record(
            AuditActions.SAAS_PAYMENT_VERIFIED, actorId = actorId, businessId = payment.businessId, entityType = "SAAS_PAYMENT", entityId = payment.id,
            metadata = mapOf("invoiceNumber" to invoice.invoiceNumber, "amount" to payment.amount, "method" to payment.method.name, "reference" to payment.transactionReference),
        )
    }

    @Transactional
    fun reject(actorId: UUID, paymentId: UUID, reason: String) {
        val payment = lockPending(paymentId)
        payment.status = SaasPaymentStatus.REJECTED
        payment.rejectedAt = clock.instant()
        payment.rejectionReason = reason.trim()
        audit.record(
            AuditActions.SAAS_PAYMENT_REJECTED, actorId = actorId, businessId = payment.businessId, entityType = "SAAS_PAYMENT", entityId = payment.id,
            metadata = mapOf("reason" to payment.rejectionReason, "reference" to payment.transactionReference),
        )
    }

    /** Comprobante de un pago SaaS: solo el SUPER_ADMIN; cada acceso queda auditado. */
    @Transactional
    fun receipt(actorId: UUID, paymentId: UUID): Path {
        val payment = payments.findById(paymentId).orElseThrow { ApiException.notFound() }
        val path = payment.receiptKey?.let { media.resolveReceipt(it) } ?: throw ApiException.notFound("Este pago no tiene comprobante.")
        audit.record(AuditActions.SAAS_RECEIPT_VIEWED, actorId = actorId, businessId = payment.businessId, entityType = "SAAS_PAYMENT", entityId = payment.id)
        return path
    }

    /**
     * Aplica un cobro pagado al contrato. Renovar el mismo plan con vigencia (o en gracia) continúa desde el
     * vencimiento; un alta, un cambio de plan o un plan ya vencido empiezan hoy (sin prorrateo).
     */
    private fun applyPaid(invoice: SaasInvoice, actorId: UUID?) {
        val sub = subscriptions.lockById(invoice.subscriptionId)!!
        val now = clock.instant()
        val continues = invoice.planId == sub.planId && sub.expiresAt != null &&
            (sub.status == SaasSubscriptionStatus.ACTIVE || sub.status == SaasSubscriptionStatus.PAST_DUE)
        val zone = ZoneId.of(businesses.findById(invoice.businessId).orElseThrow().timeZone)
        fun periodFrom(from: Instant) = from to from.atZone(zone).plus(invoice.billingPeriod.period).toInstant()
        var (start, end) = periodFrom(if (continues) sub.expiresAt!! else now)
        // Un pago muy tardío dentro de la gracia nunca deja un periodo que ya terminó.
        if (!end.isAfter(now)) {
            start = now
            end = periodFrom(now).second
        }
        val continued = continues && start == sub.expiresAt

        invoice.status = InvoiceStatus.PAID
        invoice.paidAt = now
        invoice.periodStart = start
        invoice.periodEnd = end
        sub.planId = invoice.planId
        if (!continued) sub.startAt = start
        sub.expiresAt = end
        sub.status = SaasSubscriptionStatus.ACTIVE
        sub.graceUntil = null
        sub.cancelledAt = null
        audit.record(
            AuditActions.SAAS_SUBSCRIPTION_ACTIVATED, actorId = actorId, businessId = invoice.businessId, entityType = "SAAS_SUBSCRIPTION", entityId = sub.id,
            metadata = mapOf("invoiceNumber" to invoice.invoiceNumber, "plan" to invoice.planNameSnapshot, "expiresAt" to end.toString()),
        )
    }

    /** ACTIVE vencida → PAST_DUE con plazo de gracia (o EXPIRED si la gracia es 0). Idempotente. */
    @Transactional
    fun markLapsed(subscriptionId: UUID): Boolean {
        val sub = subscriptions.lockById(subscriptionId) ?: return false
        val now = clock.instant()
        if (sub.status != SaasSubscriptionStatus.ACTIVE || sub.expiresAt!!.isAfter(now)) return false
        val graceUntil = sub.expiresAt!!.plus(Duration.ofDays(entitlements.graceDays().toLong()))
        if (graceUntil.isAfter(now)) {
            sub.status = SaasSubscriptionStatus.PAST_DUE
            sub.graceUntil = graceUntil
            audit.record(AuditActions.SAAS_SUBSCRIPTION_PAST_DUE, businessId = sub.businessId, entityType = "SAAS_SUBSCRIPTION", entityId = sub.id)
        } else {
            expire(sub)
        }
        return true
    }

    /** PAST_DUE sin pago al terminar la gracia → EXPIRED. Los datos del negocio se conservan. */
    @Transactional
    fun expireAfterGrace(subscriptionId: UUID): Boolean {
        val sub = subscriptions.lockById(subscriptionId) ?: return false
        if (sub.status != SaasSubscriptionStatus.PAST_DUE || sub.graceUntil!!.isAfter(clock.instant())) return false
        expire(sub)
        return true
    }

    private fun expire(sub: SaasSubscription) {
        sub.status = SaasSubscriptionStatus.EXPIRED
        sub.graceUntil = null
        audit.record(AuditActions.SAAS_SUBSCRIPTION_EXPIRED, businessId = sub.businessId, entityType = "SAAS_SUBSCRIPTION", entityId = sub.id)
    }

    private fun lockPending(paymentId: UUID): SaasPayment {
        val payment = payments.lockById(paymentId) ?: throw ApiException.notFound("El pago no existe.")
        if (payment.status != SaasPaymentStatus.PENDING_VERIFICATION) {
            val message = if (payment.status == SaasPaymentStatus.VERIFIED) "Este pago ya fue verificado." else "Este pago ya fue rechazado."
            throw ApiException.conflict("PAYMENT_ALREADY_PROCESSED", message)
        }
        return payment
    }

    private fun void(invoice: SaasInvoice) {
        invoice.status = InvoiceStatus.VOID
        invoice.voidedAt = clock.instant()
        invoices.flush()
    }

    private fun hasPendingPayment(invoiceId: UUID) =
        payments.findByInvoiceIdOrderByReportedAtDesc(invoiceId).any { it.status == SaasPaymentStatus.PENDING_VERIFICATION }

    private fun nextNumber(): String {
        val year = clock.instant().atZone(PLATFORM_ZONE).year
        val value = jdbc.queryForObject(
            """
            insert into saas_invoice_sequences (year, last_value) values (?, 1)
            on conflict (year) do update set last_value = saas_invoice_sequences.last_value + 1
            returning last_value
            """.trimIndent(),
            Int::class.java, year,
        )!!
        return "PLAN-$year-${value.toString().padStart(6, '0')}"
    }

    fun paymentOptions(): List<PlatformPaymentOption> = platformMethods.findAllByOrderByMethodAsc()
        .filter { it.enabled }
        .map { PlatformPaymentOption(it.method, it.method.label, it.holderName!!, it.phone!!, it.qrImageUrl) }

    fun support(): SupportContact {
        val s = settings.findById(1).orElseThrow()
        val url = s.supportWhatsapp?.let { ManualWhatsAppLinkProvider.link(it, "Hola, necesito ayuda con mi cuenta de la plataforma.") }
        return SupportContact(s.supportWhatsapp, url, s.supportEmail)
    }

    fun toResponse(i: SaasInvoice) = InvoiceResponse(
        id = i.id, invoiceNumber = i.invoiceNumber, planId = i.planId, planName = i.planNameSnapshot,
        billingPeriod = i.billingPeriod, billingPeriodLabel = i.billingPeriod.label, kind = i.kind, kindLabel = i.kind.label,
        amount = i.amount, status = i.status, periodStart = i.periodStart, periodEnd = i.periodEnd, paidAt = i.paidAt,
        createdAt = i.createdAt!!,
        payments = payments.findByInvoiceIdOrderByReportedAtDesc(i.id).map(::toSummary),
    )

    fun toSummary(p: SaasPayment) = SaasPaymentSummary(
        p.id, p.method, p.amount, p.transactionReference, p.receiptKey != null, p.status, p.reportedAt, p.verifiedAt, p.rejectionReason,
    )

    companion object {
        val PLATFORM_ZONE: ZoneId = ZoneId.of("America/Lima")
        private val REFERENCE = Regex("^[A-Z0-9-]{4,40}$")
        private val ACTIVE_PAYMENT_STATUSES = setOf(SaasPaymentStatus.PENDING_VERIFICATION, SaasPaymentStatus.VERIFIED)
    }
}
