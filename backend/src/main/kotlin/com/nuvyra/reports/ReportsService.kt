package com.nuvyra.reports

import com.nuvyra.saas.EntitlementService
import com.nuvyra.saas.PlanFeature
import com.nuvyra.businesses.BusinessService
import com.nuvyra.capacity.CapacityAllocationRepository
import com.nuvyra.capacity.CapacityService
import com.nuvyra.common.audit.AuditLogRepository
import com.nuvyra.products.ProductRepository
import com.nuvyra.products.PublicationStatus
import com.nuvyra.security.CurrentBusiness
import com.nuvyra.whatsapp.OrderMessages
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class Money(val amount: BigDecimal, val count: Long)

data class DayAmount(val date: LocalDate, val amount: BigDecimal, val count: Long)

data class SubscriptionCounts(
    val pendingActivation: Long,
    val active: Long,
    val expiringSoon: Long,
    val expired: Long,
    val capacityReleasePending: Long,
)

data class SoldOutProduct(val id: UUID, val name: String, val capacity: Int?)

data class ActivityItem(val at: Instant, val text: String, val entityType: String?, val entityId: UUID?)

/** Solo dinero de ventas del vendedor (pagos VERIFIED). Nunca incluye facturación SaaS. */
data class DashboardResponse(
    val timeZone: String,
    val confirmedToday: Money,
    val confirmedMonth: Money,
    val pendingVerification: Money,
    /** Renovaciones con pago verificado este mes (ya incluidas en los ingresos confirmados). */
    val renewalsMonth: Money,
    val notificationsDue: Long,
    val customers: Long,
    val subscriptions: SubscriptionCounts,
    val soldOutProducts: List<SoldOutProduct>,
    val last30Days: List<DayAmount>,
    val recentActivity: List<ActivityItem>,
)

data class ProductSales(val productId: UUID, val productName: String, val amount: BigDecimal, val count: Long)

data class StatusCount(val status: String, val count: Long, val amount: BigDecimal)

data class RecurringCustomer(val customerId: UUID, val fullName: String, val phone: String, val paidOrders: Long, val amount: BigDecimal)

data class SalesReport(
    val from: LocalDate,
    val to: LocalDate,
    val confirmed: Money,
    val pending: Money,
    val byDay: List<DayAmount>,
    val byProduct: List<ProductSales>,
    val ordersByStatus: List<StatusCount>,
    val recurringCustomers: Long,
    val topRecurring: List<RecurringCustomer>,
    /** Suscripciones antiguas registradas a mano: informativo, no son ingresos del periodo. */
    val manualSubscriptionsRegistered: Long,
    val renewals: RenewalStats,
)

/** Renovaciones del periodo: pagadas (incluidas en `confirmed`) y respuestas de solicitudes que vencían en él. */
data class RenewalStats(val confirmed: Money, val accepted: Long, val declined: Long, val noResponse: Long, val pending: Long)

@Service
class ReportsService(
    private val jdbc: JdbcTemplate,
    private val products: ProductRepository,
    private val capacity: CapacityService,
    private val allocations: CapacityAllocationRepository,
    private val auditLogs: AuditLogRepository,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun dashboard(businessId: UUID, zone: ZoneId): DashboardResponse {
        val now = clock.instant()
        val today = now.atZone(zone).toLocalDate()
        val startOfDay = today.atStartOfDay(zone).toInstant()
        val startOfMonth = today.withDayOfMonth(1).atStartOfDay(zone).toInstant()
        val tomorrow = today.plusDays(1).atStartOfDay(zone).toInstant()

        val published = products.findByBusinessIdAndPublicationStatusOrderByCreatedAtDesc(businessId, PublicationStatus.PUBLISHED)
        val availability = capacity.availabilityFor(published)
        return DashboardResponse(
            timeZone = zone.id,
            confirmedToday = verified(businessId, startOfDay, tomorrow),
            confirmedMonth = verified(businessId, startOfMonth, tomorrow),
            pendingVerification = jdbc.queryForObject(
                "select coalesce(sum(amount), 0), count(*) from customer_payments where business_id = ? and status = 'PENDING_VERIFICATION'",
                { rs, _ -> Money(rs.getBigDecimal(1), rs.getLong(2)) }, businessId,
            )!!,
            renewalsMonth = paidRenewals(businessId, startOfMonth, tomorrow),
            notificationsDue = jdbc.queryForObject(
                "select count(*) from renewal_notifications where business_id = ? and delivery_status in ('PENDING', 'FAILED') and scheduled_at <= ?",
                Long::class.java, businessId, ts(now),
            )!!,
            customers = jdbc.queryForObject("select count(*) from customers where business_id = ?", Long::class.java, businessId)!!,
            subscriptions = subscriptionCounts(businessId, now),
            soldOutProducts = published.filter { availability.getValue(it.id).soldOut }.map { SoldOutProduct(it.id, it.name, it.capacity) },
            last30Days = byDay(businessId, zone, today.minusDays(29), today),
            recentActivity = recentActivity(businessId),
        )
    }

    @Transactional(readOnly = true)
    fun sales(businessId: UUID, zone: ZoneId, from: LocalDate, to: LocalDate): SalesReport {
        val start = from.atStartOfDay(zone).toInstant()
        val end = to.plusDays(1).atStartOfDay(zone).toInstant()
        val recurring = jdbc.query(
            """
            select c.id, c.full_name, c.phone, count(*) as paid, sum(o.total_amount) as amount
            from orders o join customers c on c.id = o.customer_id
            where o.business_id = ? and o.status = 'PAID' and o.paid_at >= ? and o.paid_at < ?
            group by c.id, c.full_name, c.phone having count(*) > 1
            order by paid desc, amount desc
            """.trimIndent(),
            { rs, _ -> RecurringCustomer(rs.getObject(1, UUID::class.java), rs.getString(2), rs.getString(3), rs.getLong(4), rs.getBigDecimal(5)) },
            businessId, ts(start), ts(end),
        )
        return SalesReport(
            from = from,
            to = to,
            confirmed = verified(businessId, start, end),
            pending = jdbc.queryForObject(
                """
                select coalesce(sum(amount), 0), count(*) from customer_payments
                where business_id = ? and status = 'PENDING_VERIFICATION' and reported_at >= ? and reported_at < ?
                """.trimIndent(),
                { rs, _ -> Money(rs.getBigDecimal(1), rs.getLong(2)) }, businessId, ts(start), ts(end),
            )!!,
            byDay = byDay(businessId, zone, from, to),
            byProduct = jdbc.query(
                """
                select i.product_id, max(i.product_name_snapshot), coalesce(sum(p.amount), 0), count(*)
                from customer_payments p join order_items i on i.order_id = p.order_id
                where p.business_id = ? and p.status = 'VERIFIED' and p.verified_at >= ? and p.verified_at < ?
                group by i.product_id order by 3 desc
                """.trimIndent(),
                { rs, _ -> ProductSales(rs.getObject(1, UUID::class.java), rs.getString(2), rs.getBigDecimal(3), rs.getLong(4)) },
                businessId, ts(start), ts(end),
            ),
            ordersByStatus = jdbc.query(
                """
                select status, count(*), coalesce(sum(total_amount), 0) from orders
                where business_id = ? and created_at >= ? and created_at < ? group by status order by 2 desc
                """.trimIndent(),
                { rs, _ -> StatusCount(rs.getString(1), rs.getLong(2), rs.getBigDecimal(3)) },
                businessId, ts(start), ts(end),
            ),
            recurringCustomers = recurring.size.toLong(),
            topRecurring = recurring.take(5),
            manualSubscriptionsRegistered = jdbc.queryForObject(
                "select count(*) from customer_subscriptions where business_id = ? and origin <> 'ORDER' and created_at >= ? and created_at < ?",
                Long::class.java, businessId, ts(start), ts(end),
            )!!,
            renewals = renewalStats(businessId, start, end),
        )
    }

    private fun paidRenewals(businessId: UUID, from: Instant, to: Instant): Money = jdbc.queryForObject(
        """
        select coalesce(sum(total_amount), 0), count(*) from orders
        where business_id = ? and kind = 'RENEWAL' and status = 'PAID' and paid_at >= ? and paid_at < ?
        """.trimIndent(),
        { rs, _ -> Money(rs.getBigDecimal(1), rs.getLong(2)) }, businessId, ts(from), ts(to),
    )!!

    private fun renewalStats(businessId: UUID, from: Instant, to: Instant): RenewalStats {
        val byStatus = jdbc.query(
            "select status, count(*) from renewal_requests where business_id = ? and cycle_expires_at >= ? and cycle_expires_at < ? group by status",
            { rs, _ -> rs.getString(1) to rs.getLong(2) }, businessId, ts(from), ts(to),
        ).toMap()
        return RenewalStats(
            confirmed = paidRenewals(businessId, from, to),
            accepted = (byStatus["AWAITING_PAYMENT"] ?: 0) + (byStatus["COMPLETED"] ?: 0),
            declined = byStatus["DECLINED"] ?: 0,
            noResponse = byStatus["EXPIRED"] ?: 0,
            pending = byStatus["OPEN"] ?: 0,
        )
    }

    private fun verified(businessId: UUID, from: Instant, to: Instant): Money = jdbc.queryForObject(
        """
        select coalesce(sum(amount), 0), count(*) from customer_payments
        where business_id = ? and status = 'VERIFIED' and verified_at >= ? and verified_at < ?
        """.trimIndent(),
        { rs, _ -> Money(rs.getBigDecimal(1), rs.getLong(2)) }, businessId, ts(from), ts(to),
    )!!

    /** Serie diaria de ingresos confirmados, con días sin ventas en cero. */
    private fun byDay(businessId: UUID, zone: ZoneId, from: LocalDate, to: LocalDate): List<DayAmount> {
        val rows = jdbc.query(
            """
            select (verified_at at time zone ?)::date as day, sum(amount), count(*)
            from customer_payments
            where business_id = ? and status = 'VERIFIED' and verified_at >= ? and verified_at < ?
            group by day
            """.trimIndent(),
            { rs, _ -> DayAmount(rs.getObject(1, LocalDate::class.java), rs.getBigDecimal(2), rs.getLong(3)) },
            zone.id, businessId, ts(from.atStartOfDay(zone).toInstant()), ts(to.plusDays(1).atStartOfDay(zone).toInstant()),
        ).associateBy { it.date }
        return generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }
            .map { rows[it] ?: DayAmount(it, BigDecimal.ZERO, 0) }.toList()
    }

    private fun subscriptionCounts(businessId: UUID, now: Instant): SubscriptionCounts {
        val counts = jdbc.query(
            "select status, count(*) from customer_subscriptions where business_id = ? group by status",
            { rs, _ -> rs.getString(1) to rs.getLong(2) }, businessId,
        ).toMap()
        val expiringSoon = jdbc.queryForObject(
            "select count(*) from customer_subscriptions where business_id = ? and status in ('ACTIVE', 'RENEWAL_PENDING') and expires_at < ?",
            Long::class.java, businessId, ts(now.plus(Duration.ofDays(EXPIRING_SOON_DAYS))),
        )!!
        return SubscriptionCounts(
            pendingActivation = counts["PENDING_ACTIVATION"] ?: 0,
            active = (counts["ACTIVE"] ?: 0) + (counts["RENEWAL_PENDING"] ?: 0),
            expiringSoon = expiringSoon,
            expired = counts["EXPIRED"] ?: 0,
            capacityReleasePending = allocations.countReleasePending(businessId),
        )
    }

    private fun recentActivity(businessId: UUID): List<ActivityItem> =
        auditLogs.findTop12ByBusinessIdOrderByCreatedAtDesc(businessId).mapNotNull { log ->
            val meta = runCatching { objectMapper.readValue(log.metadata, Map::class.java) }.getOrDefault(emptyMap<String, Any>())
            ActivityText.of(log.action, meta)?.let { ActivityItem(log.createdAt, it, log.entityType, log.entityId) }
        }.take(8)

    private fun ts(instant: Instant) = Timestamp.from(instant)

    companion object {
        /** "Por vencer" del panel: coincide con el primer recordatorio (3 días antes). */
        const val EXPIRING_SOON_DAYS = 3L
    }
}

/** Texto legible para el vendedor a partir del registro de auditoría. */
object ActivityText {
    fun of(action: String, meta: Map<*, *>): String? {
        val order = meta["orderNumber"]?.toString()
        return when (action) {
            "ORDER_CREATED" -> "Nuevo pedido $order"
            "PAYMENT_REPORTED" -> "Pago reportado en $order"
            "PAYMENT_VERIFIED" -> "Pago verificado de $order · " +
                (meta["amount"]?.toString()?.toBigDecimalOrNull()?.let(OrderMessages::formatSoles) ?: "")
            "PAYMENT_REJECTED" -> "Pago rechazado en $order"
            "ORDER_CANCELLED" -> "Pedido $order cancelado"
            "ORDER_EXPIRED" -> "Pedido $order venció sin pago"
            "SUBSCRIPTION_CREATED" -> "Suscripción por activar ($order)"
            "SUBSCRIPTION_ACTIVATED" -> "Suscripción activada: ${meta["product"]}"
            "SUBSCRIPTION_EXPIRED" -> "Suscripción vencida: ${meta["product"]}"
            "SUBSCRIPTION_CANCELLED" -> "Suscripción cancelada"
            "SUBSCRIPTION_REGISTERED" -> "Cliente antiguo registrado: ${meta["customer"]} · ${meta["product"]}"
            "CAPACITY_RELEASED" -> "Cupo liberado: ${meta["product"]}"
            "RENEWAL_ACCEPTED" -> "Un cliente aceptó renovar ${meta["product"]}"
            "RENEWAL_DECLINED" -> "Un cliente no renovará ${meta["product"]}"
            "RENEWAL_COMPLETED" -> "Renovación confirmada: ${meta["product"]} ($order)"
            "RENEWAL_ORDER_CREATED" -> "Pedido de renovación $order"
            "CUSTOMER_CREATED" -> "Cliente registrado: ${meta["name"]}"
            "PRODUCT_CREATED" -> "Producto creado"
            "PRODUCT_STATUS_CHANGED" -> if (meta["to"] == "PUBLISHED") "Producto publicado" else null
            else -> null
        }
    }
}

@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Reportes")
class ReportsController(
    private val reports: ReportsService,
    private val businesses: BusinessService,
    private val entitlements: EntitlementService,
    private val clock: Clock,
) {

    @GetMapping("/dashboard")
    @Operation(summary = "Indicadores del panel. Solo los pagos verificados cuentan como ingresos.")
    fun dashboard(current: CurrentBusiness) = reports.dashboard(current.businessId, zone(current))

    @GetMapping("/sales")
    @Operation(summary = "Reporte de ventas por periodo, producto, estado y clientes recurrentes")
    fun sales(
        current: CurrentBusiness,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
    ): SalesReport {
        entitlements.requireFeature(current.businessId, PlanFeature.REPORTS)
        val zone = zone(current)
        val today = clock.instant().atZone(zone).toLocalDate()
        val end = to ?: today
        val start = (from ?: end.withDayOfMonth(1)).let { if (it.isAfter(end)) end else it }
        // Máximo un año por consulta para mantener la respuesta rápida.
        return reports.sales(current.businessId, zone, maxOf(start, end.minusDays(366)), end)
    }

    private fun zone(current: CurrentBusiness) = ZoneId.of(businesses.get(current.businessId).timeZone)
}
