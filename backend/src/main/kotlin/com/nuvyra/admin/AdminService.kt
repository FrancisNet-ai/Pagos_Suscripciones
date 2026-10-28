package com.nuvyra.admin

import com.nuvyra.businesses.BusinessRepository
import com.nuvyra.businesses.BusinessStatus
import com.nuvyra.common.audit.AuditActions
import com.nuvyra.common.audit.AuditService
import com.nuvyra.common.error.ApiException
import com.nuvyra.common.validation.PeruPhone
import com.nuvyra.common.web.PageResponse
import com.nuvyra.media.MediaStorage
import com.nuvyra.payments.PaymentMethodType
import com.nuvyra.saas.AccessSummary
import com.nuvyra.saas.BillingService
import com.nuvyra.saas.EntitlementService
import com.nuvyra.saas.PlatformPaymentMethod
import com.nuvyra.saas.PlatformPaymentMethodRepository
import com.nuvyra.saas.PlatformPaymentMethodRequest
import com.nuvyra.saas.PlatformPaymentMethodResponse
import com.nuvyra.saas.PlatformSettingsRepository
import com.nuvyra.saas.PlatformSettingsRequest
import com.nuvyra.saas.PlatformSettingsResponse
import com.nuvyra.saas.SaasInvoiceRepository
import com.nuvyra.saas.SaasPaymentStatus
import com.nuvyra.saas.SaasSubscriptionStatus
import org.springframework.data.domain.PageRequest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.math.BigDecimal
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.ceil

/**
 * Panel del SUPER_ADMIN. Trabaja con agregados: no expone compradores, ventas individuales ni
 * comprobantes de los vendedores. Los ingresos que muestra son solo los cobros SaaS verificados.
 */
@Service
class AdminService(
    private val jdbc: JdbcTemplate,
    private val businesses: BusinessRepository,
    private val invoices: SaasInvoiceRepository,
    private val billing: BillingService,
    private val entitlements: EntitlementService,
    private val settings: PlatformSettingsRepository,
    private val platformMethods: PlatformPaymentMethodRepository,
    private val media: MediaStorage,
    private val audit: AuditService,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun overview(): AdminOverview {
        val now = clock.instant()
        val zone = BillingService.PLATFORM_ZONE
        val month = YearMonth.from(now.atZone(zone))
        val monthStart = month.atDay(1).atStartOfDay(zone).toInstant()
        val firstMonth = month.minusMonths(11)
        fun count(sql: String, vararg args: Any) = jdbc.queryForObject(sql, Long::class.java, *args)!!
        fun subs(status: SaasSubscriptionStatus) = count("select count(*) from saas_subscriptions where status = ?", status.name)

        val byMonth = jdbc.query(
            """
            select to_char(date_trunc('month', verified_at at time zone 'America/Lima'), 'YYYY-MM') as m, sum(amount)
            from saas_payments where status = 'VERIFIED' and verified_at >= ?
            group by 1
            """.trimIndent(),
            { rs, _ -> rs.getString(1) to rs.getBigDecimal(2) },
            Timestamp.from(firstMonth.atDay(1).atStartOfDay(zone).toInstant()),
        ).toMap()
        val labels = DateTimeFormatter.ofPattern("MMM yyyy", Locale.forLanguageTag("es-PE"))

        return AdminOverview(
            businessesTotal = count("select count(*) from businesses"),
            businessesActive = count("select count(*) from businesses b where ${EntitlementService.entitledSql()}", Timestamp.from(now)),
            businessesNoPlan = count(
                "select count(*) from businesses b where b.status = 'ACTIVE' and not exists " +
                    "(select 1 from saas_subscriptions s where s.business_id = b.id and s.status <> 'CANCELLED')",
            ),
            subscriptionsPendingPayment = subs(SaasSubscriptionStatus.PENDING_PAYMENT),
            subscriptionsPastDue = subs(SaasSubscriptionStatus.PAST_DUE),
            subscriptionsExpired = subs(SaasSubscriptionStatus.EXPIRED),
            businessesSuspended = count("select count(*) from businesses where status = 'SUSPENDED'"),
            paymentsToVerify = count("select count(*) from saas_payments where status = 'PENDING_VERIFICATION'"),
            revenueMonth = sum("select coalesce(sum(amount), 0) from saas_payments where status = 'VERIFIED' and verified_at >= ?", Timestamp.from(monthStart)),
            revenueTotal = sum("select coalesce(sum(amount), 0) from saas_payments where status = 'VERIFIED'"),
            newBusinesses30Days = count("select count(*) from businesses where created_at >= ?", Timestamp.from(now.minus(Duration.ofDays(30)))),
            plans = jdbc.query(
                """
                select p.id, p.name, count(s.id) from saas_plans p
                left join saas_subscriptions s on s.plan_id = p.id and s.status in ('ACTIVE', 'PAST_DUE')
                group by p.id, p.name, p.sort_order order by p.sort_order, p.name
                """.trimIndent(),
            ) { rs, _ -> PlanCount(rs.getObject(1, UUID::class.java), rs.getString(2), rs.getLong(3)) },
            last12Months = (0L..11L).map { firstMonth.plusMonths(it) }.map { m ->
                val key = m.toString()
                MonthAmount(key, m.atDay(1).format(labels).replace(".", ""), byMonth[key] ?: BigDecimal.ZERO.setScale(2))
            },
        )
    }

    @Transactional(readOnly = true)
    fun businesses(query: String?, filter: BusinessFilter, page: Int, size: Int): PageResponse<AdminBusinessSummary> {
        val where = mutableListOf("true")
        val args = mutableListOf<Any>()
        query?.trim()?.takeIf { it.isNotEmpty() }?.let { q ->
            val like = "%" + q.lowercase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
            where += "(lower(b.business_name) like ? or b.slug like ? or u.email like ? or b.unique_code = ?)"
            args.addAll(listOf(like, like, like, q.uppercase()))
        }
        when (filter) {
            BusinessFilter.ALL -> Unit
            BusinessFilter.ACTIVE -> {
                where += EntitlementService.entitledSql()
                args += Timestamp.from(clock.instant())
            }
            BusinessFilter.NO_PLAN -> where += "b.status = 'ACTIVE' and (s.id is null or s.status = 'CANCELLED')"
            BusinessFilter.SUSPENDED -> where += "b.status = 'SUSPENDED'"
            else -> {
                where += "s.status = ?"
                args += filter.name
            }
        }
        val from = """
            from businesses b join users u on u.id = b.owner_id
            left join saas_subscriptions s on s.business_id = b.id
            left join saas_plans p on p.id = s.plan_id
            where ${where.joinToString(" and ")}
        """.trimIndent()
        val total = jdbc.queryForObject("select count(*) $from", Long::class.java, *args.toTypedArray())!!
        val pageSize = size.coerceIn(1, PageResponse.MAX_SIZE)
        val items = jdbc.query(
            "$SUMMARY_COLUMNS $from order by b.created_at desc limit ? offset ?",
            { rs, _ -> summary(rs) },
            *(args + listOf(pageSize, page.coerceAtLeast(0) * pageSize)).toTypedArray(),
        )
        return PageResponse(items, page, pageSize, total, ceil(total / pageSize.toDouble()).toInt())
    }

    @Transactional(readOnly = true)
    fun business(businessId: UUID): AdminBusinessDetail {
        val business = businesses.findById(businessId).orElseThrow { ApiException.notFound("El negocio no existe.") }
        val summary = jdbc.query(
            """
            $SUMMARY_COLUMNS from businesses b join users u on u.id = b.owner_id
            left join saas_subscriptions s on s.business_id = b.id left join saas_plans p on p.id = s.plan_id
            where b.id = ?
            """.trimIndent(),
            { rs, _ -> summary(rs) }, businessId,
        ).first()
        val ownerPhone = jdbc.queryForObject("select u.phone from users u where u.id = ?", String::class.java, business.ownerId)!!
        val usage = entitlements.usage(businessId)
        return AdminBusinessDetail(
            summary = summary,
            ownerPhone = ownerPhone,
            suspensionReason = business.suspensionReason,
            suspendedAt = business.suspendedAt,
            access = AccessSummary.of(entitlements.access(business)),
            usage = BusinessUsage(
                products = usage.products,
                customers = usage.customers,
                ordersPaid = jdbc.queryForObject("select count(*) from orders where business_id = ? and status = 'PAID'", Long::class.java, businessId)!!,
            ),
            invoices = invoices.findByBusinessIdOrderByCreatedAtDesc(businessId, PageRequest.of(0, 24)).map(billing::toResponse),
            activity = auditLogs(businessId, null, platformOnly = true, page = 0, size = 30).items,
            supportAccessUntil = jdbc.query(
                "select max(expires_at) from support_grants where business_id = ? and revoked_at is null and expires_at > ?",
                { rs, _ -> rs.getTimestamp(1)?.toInstant() }, businessId, java.sql.Timestamp.from(clock.instant()),
            ).firstOrNull(),
        )
    }

    /** La suspensión no borra datos ni cambia las fechas pagadas; cierra la tienda y bloquea el panel comercial. */
    @Transactional
    fun suspend(actorId: UUID, businessId: UUID, reason: String) {
        val business = businesses.findById(businessId).orElseThrow { ApiException.notFound("El negocio no existe.") }
        if (business.status == BusinessStatus.SUSPENDED) throw ApiException.conflict("ALREADY_SUSPENDED", "Este negocio ya está suspendido.")
        business.status = BusinessStatus.SUSPENDED
        business.suspensionReason = reason.trim()
        business.suspendedAt = clock.instant()
        audit.record(
            AuditActions.BUSINESS_SUSPENDED, actorId = actorId, businessId = businessId, entityType = "BUSINESS", entityId = businessId,
            metadata = mapOf("reason" to business.suspensionReason),
        )
    }

    @Transactional
    fun reactivate(actorId: UUID, businessId: UUID) {
        val business = businesses.findById(businessId).orElseThrow { ApiException.notFound("El negocio no existe.") }
        if (business.status != BusinessStatus.SUSPENDED) throw ApiException.conflict("NOT_SUSPENDED", "Este negocio no está suspendido.")
        business.status = BusinessStatus.ACTIVE
        business.suspensionReason = null
        business.suspendedAt = null
        audit.record(AuditActions.BUSINESS_REACTIVATED, actorId = actorId, businessId = businessId, entityType = "BUSINESS", entityId = businessId)
    }

    @Transactional(readOnly = true)
    fun payments(status: SaasPaymentStatus?, page: Int, size: Int): PageResponse<AdminSaasPayment> {
        val pageSize = size.coerceIn(1, PageResponse.MAX_SIZE)
        val where = if (status != null) "where p.status = ?" else ""
        val args = listOfNotNull<Any>(status?.name)
        val total = jdbc.queryForObject("select count(*) from saas_payments p $where", Long::class.java, *args.toTypedArray())!!
        // Pendientes: los más antiguos primero (orden de atención); el resto, los más recientes.
        val order = if (status == SaasPaymentStatus.PENDING_VERIFICATION) "p.reported_at asc" else "p.reported_at desc"
        val items = jdbc.query(
            """
            select p.id, p.business_id, b.business_name, p.invoice_id, i.invoice_number, i.plan_name_snapshot, p.method,
                   p.receiver_holder_snapshot, p.receiver_phone_snapshot, p.amount, p.transaction_reference, p.receipt_key,
                   p.status, p.reported_at, p.verified_at, p.rejection_reason
            from saas_payments p join businesses b on b.id = p.business_id join saas_invoices i on i.id = p.invoice_id
            $where order by $order limit ? offset ?
            """.trimIndent(),
            { rs, _ ->
                AdminSaasPayment(
                    id = rs.getObject(1, UUID::class.java), businessId = rs.getObject(2, UUID::class.java), businessName = rs.getString(3),
                    invoiceId = rs.getObject(4, UUID::class.java), invoiceNumber = rs.getString(5), planName = rs.getString(6),
                    method = PaymentMethodType.valueOf(rs.getString(7)), receiverHolder = rs.getString(8), receiverPhone = rs.getString(9),
                    amount = rs.getBigDecimal(10), transactionReference = rs.getString(11), hasReceipt = rs.getString(12) != null,
                    status = SaasPaymentStatus.valueOf(rs.getString(13)), reportedAt = rs.getTimestamp(14).toInstant(),
                    verifiedAt = rs.getTimestamp(15)?.toInstant(), rejectionReason = rs.getString(16),
                )
            },
            *(args + listOf(pageSize, page.coerceAtLeast(0) * pageSize)).toTypedArray(),
        )
        return PageResponse(items, page, pageSize, total, ceil(total / pageSize.toDouble()).toInt())
    }

    @Transactional(readOnly = true)
    fun auditLogs(businessId: UUID?, action: String?, platformOnly: Boolean, page: Int, size: Int): PageResponse<AdminAuditEntry> {
        val pageSize = size.coerceIn(1, PageResponse.MAX_SIZE)
        val where = mutableListOf("true")
        val args = mutableListOf<Any>()
        businessId?.let { where += "a.business_id = ?"; args += it }
        action?.takeIf { it.isNotBlank() }?.let { where += "a.action = ?"; args += it.trim().uppercase() }
        if (platformOnly) {
            where += "(a.action like 'SAAS\\_%' or a.action in (${PLATFORM_ACTIONS.joinToString { "?" }}))"
            args.addAll(PLATFORM_ACTIONS)
        }
        val clause = where.joinToString(" and ")
        val total = jdbc.queryForObject("select count(*) from audit_logs a where $clause", Long::class.java, *args.toTypedArray())!!
        val items = jdbc.query(
            """
            select a.id, a.action, u.email, a.business_id, b.business_name, a.entity_type, a.metadata, a.ip_address, a.created_at
            from audit_logs a left join users u on u.id = a.actor_id left join businesses b on b.id = a.business_id
            where $clause order by a.created_at desc limit ? offset ?
            """.trimIndent(),
            { rs, _ ->
                val act = rs.getString(2)
                val visible = isPlatformAction(act)
                AdminAuditEntry(
                    id = rs.getObject(1, UUID::class.java), action = act, actorEmail = rs.getString(3),
                    businessId = rs.getObject(4, UUID::class.java), businessName = rs.getString(5), entityType = rs.getString(6),
                    metadata = if (visible) rs.getString(7) else null,
                    ipAddress = if (visible) rs.getString(8) else null,
                    createdAt = rs.getTimestamp(9).toInstant(),
                )
            },
            *(args + listOf(pageSize, page.coerceAtLeast(0) * pageSize)).toTypedArray(),
        )
        return PageResponse(items, page, pageSize, total, ceil(total / pageSize.toDouble()).toInt())
    }

    @Transactional(readOnly = true)
    fun settings(): PlatformSettingsResponse {
        val s = settings.findById(1).orElseThrow()
        return PlatformSettingsResponse(
            s.supportWhatsapp, s.supportEmail, s.saasGraceDays,
            platformMethods.findAllByOrderByMethodAsc().map(::toResponse),
        )
    }

    @Transactional
    fun updateSettings(actorId: UUID, request: PlatformSettingsRequest): PlatformSettingsResponse {
        val s = settings.findById(1).orElseThrow()
        s.supportWhatsapp = request.supportWhatsapp?.takeIf { it.isNotBlank() }?.let { PeruPhone.normalize(it) }
        s.supportEmail = request.supportEmail?.trim()?.lowercase()?.ifEmpty { null }
        s.saasGraceDays = request.saasGraceDays!!
        audit.record(
            AuditActions.PLATFORM_SETTINGS_UPDATED, actorId = actorId, entityType = "PLATFORM_SETTINGS",
            metadata = mapOf("graceDays" to s.saasGraceDays, "supportWhatsapp" to (s.supportWhatsapp != null), "supportEmail" to (s.supportEmail != null)),
        )
        return settings()
    }

    @Transactional
    fun updatePaymentMethod(actorId: UUID, method: PaymentMethodType, request: PlatformPaymentMethodRequest): PlatformSettingsResponse {
        val m = platformMethods.findById(method).orElseThrow()
        val holder = request.holderName?.trim()?.ifEmpty { null }
        val phone = request.phone?.takeIf { it.isNotBlank() }?.let { PeruPhone.normalize(it) }
        if (request.enabled!! && holder == null) throw ApiException.invalidField("holderName", "Ingresa el titular para habilitar este método")
        if (request.enabled && phone == null) throw ApiException.invalidField("phone", "Ingresa el celular para habilitar este método")
        m.holderName = holder
        m.phone = phone
        m.enabled = request.enabled
        audit.record(
            AuditActions.PLATFORM_SETTINGS_UPDATED, actorId = actorId, entityType = "PLATFORM_PAYMENT_METHOD",
            metadata = mapOf("method" to method.name, "enabled" to m.enabled),
        )
        return settings()
    }

    @Transactional
    fun replaceQr(actorId: UUID, method: PaymentMethodType, file: MultipartFile?): PlatformSettingsResponse {
        val m = platformMethods.findById(method).orElseThrow()
        val previous = m.qrImageUrl
        m.qrImageUrl = file?.let { media.storeImage(it) }
        media.deleteAfterCommit(previous)
        audit.record(
            AuditActions.PLATFORM_SETTINGS_UPDATED, actorId = actorId, entityType = "PLATFORM_PAYMENT_METHOD",
            metadata = mapOf("method" to method.name, "qr" to (file != null)),
        )
        return settings()
    }

    private fun toResponse(m: PlatformPaymentMethod) =
        PlatformPaymentMethodResponse(m.method, m.method.label, m.holderName, m.phone, m.qrImageUrl, m.enabled)

    private fun sum(sql: String, vararg args: Any): BigDecimal = jdbc.queryForObject(sql, BigDecimal::class.java, *args)!!

    private fun summary(rs: ResultSet) = AdminBusinessSummary(
        id = rs.getObject(1, UUID::class.java),
        businessName = rs.getString(2),
        slug = rs.getString(3),
        uniqueCode = rs.getString(4),
        ownerName = rs.getString(5),
        ownerEmail = rs.getString(6),
        status = BusinessStatus.valueOf(rs.getString(7)),
        planName = rs.getString(8),
        subscriptionStatus = rs.getString(9)?.let(SaasSubscriptionStatus::valueOf),
        expiresAt = rs.getTimestamp(10)?.toInstant(),
        createdAt = rs.getTimestamp(11).toInstant(),
    )

    companion object {
        private const val SUMMARY_COLUMNS = "select b.id, b.business_name, b.slug, b.unique_code, u.full_name, u.email, b.status, " +
            "p.name, s.status, s.expires_at, b.created_at"

        /** Acciones cuyo detalle puede ver el SUPER_ADMIN (no contienen datos de compradores). */
        private val PLATFORM_ACTIONS = listOf(
            AuditActions.USER_REGISTERED, AuditActions.LOGIN_SUCCEEDED, AuditActions.LOGIN_FAILED, AuditActions.LOGOUT,
            AuditActions.PASSWORD_RESET_REQUESTED, AuditActions.PASSWORD_RESET_COMPLETED, AuditActions.SUPER_ADMIN_BOOTSTRAPPED,
            AuditActions.BUSINESS_SUSPENDED, AuditActions.BUSINESS_REACTIVATED, AuditActions.PLATFORM_SETTINGS_UPDATED,
            AuditActions.BUSINESS_SLUG_CHANGED,
        )

        fun isPlatformAction(action: String) = action.startsWith("SAAS_") || action in PLATFORM_ACTIONS
    }
}
