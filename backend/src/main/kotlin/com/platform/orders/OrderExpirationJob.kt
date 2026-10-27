package com.platform.orders

import com.platform.capacity.CapacityService
import com.platform.common.audit.AuditActions
import com.platform.common.audit.AuditService
import com.platform.common.config.AppProperties
import com.platform.payments.CustomerPaymentRepository
import com.platform.payments.PaymentStatus
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.util.UUID

/**
 * Tareas idempotentes y por lotes (ver ADR-001):
 * 1. Marca como EXPIRED las reservas de cupo vencidas de pedidos aún sin pago reportado.
 * 2. Vence los pedidos sin pago reportado tras `app.checkout.order-ttl`.
 * Ejecutarlas dos veces o tras un reinicio no duplica resultados: cada cambio exige el estado previo.
 */
@Component
class OrderExpirationJob(
    private val orders: OrderRepository,
    private val payments: CustomerPaymentRepository,
    private val capacity: CapacityService,
    private val audit: AuditService,
    private val jdbc: JdbcTemplate,
    private val tx: TransactionTemplate,
    private val properties: AppProperties,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT1M")
    fun run() {
        val expiredReservations = expireReservations()
        val expiredOrders = expireStaleOrders()
        if (expiredReservations + expiredOrders > 0) {
            log.info("Expiración: {} reservas y {} pedidos", expiredReservations, expiredOrders)
        }
    }

    fun expireReservations(): Int = jdbc.update(
        """
        update capacity_allocations a
        set allocation_status = 'EXPIRED', reserved_until = null, version = version + 1, updated_at = now()
        where a.allocation_status = 'RESERVED' and a.reserved_until < ?
          and exists (select 1 from orders o where o.id = a.order_id and o.status = 'AWAITING_PAYMENT')
        """.trimIndent(),
        java.sql.Timestamp.from(clock.instant()),
    )

    fun expireStaleOrders(batchSize: Int = 200): Int {
        val before = clock.instant().minus(properties.checkout.orderTtl)
        var total = 0
        while (true) {
            val ids = orders.findStaleAwaitingPayment(before, PageRequest.of(0, batchSize))
            if (ids.isEmpty()) return total
            ids.forEach { id -> if (tx.execute { expire(id) } == true) total++ }
            if (ids.size < batchSize) return total
        }
    }

    private fun expire(orderId: UUID): Boolean {
        val order = orders.findById(orderId).orElse(null) ?: return false
        if (order.status != OrderStatus.AWAITING_PAYMENT) return false
        order.status = OrderStatus.EXPIRED
        payments.findByOrderIdOrderByCreatedAtDesc(orderId).filter { it.status == PaymentStatus.PENDING }.forEach { it.status = PaymentStatus.CANCELLED }
        capacity.expireReservationForOrder(order.businessId, orderId)
        audit.record(AuditActions.ORDER_EXPIRED, businessId = order.businessId, entityType = "ORDER", entityId = orderId, metadata = mapOf("orderNumber" to order.orderNumber))
        return true
    }
}
