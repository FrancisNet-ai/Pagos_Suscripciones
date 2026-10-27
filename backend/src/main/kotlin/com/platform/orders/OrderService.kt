package com.platform.orders

import com.platform.capacity.CapacityService
import com.platform.common.audit.AuditActions
import com.platform.common.audit.AuditService
import com.platform.common.error.ApiException
import com.platform.common.web.PageResponse
import com.platform.customers.ConsentPurpose
import com.platform.customers.CustomerConsentRepository
import com.platform.payments.CustomerPayment
import com.platform.payments.CustomerPaymentRepository
import com.platform.payments.PaymentStatus
import com.platform.products.ProductService
import com.platform.subscriptions.SubscriptionLabels
import com.platform.subscriptions.SubscriptionService
import com.platform.whatsapp.ManualWhatsAppLinkProvider
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@Service
class OrderService(
    private val orders: OrderRepository,
    private val items: OrderItemRepository,
    private val payments: CustomerPaymentRepository,
    private val consents: CustomerConsentRepository,
    private val capacity: CapacityService,
    private val subscriptions: SubscriptionService,
    private val audit: AuditService,
) {
    /** Ventas del negocio. Las fechas se interpretan en la zona horaria del negocio. */
    @Transactional(readOnly = true)
    fun list(
        businessId: UUID,
        zone: ZoneId,
        filter: OrderFilter,
        from: LocalDate?,
        to: LocalDate?,
        productId: UUID?,
        query: String?,
        page: Int,
        size: Int,
    ): PageResponse<OrderSummary> {
        val start = from?.atStartOfDay(zone)?.toInstant() ?: Instant.EPOCH
        val end = to?.plusDays(1)?.atStartOfDay(zone)?.toInstant() ?: Instant.parse("9999-12-31T00:00:00Z")
        val pageable = PageRequest.of(maxOf(page, 0), size.coerceIn(1, PageResponse.MAX_SIZE), Sort.by(Sort.Direction.DESC, "createdAt"))
        val result = orders.search(
            businessId, filter.statuses, start, end, productId != null, productId ?: UUID(0, 0),
            ProductService.likePattern(query), pageable,
        )
        val ids = result.content.map { it.id }
        val itemsByOrder = if (ids.isEmpty()) emptyMap() else items.findByOrderIdIn(ids).groupBy { it.orderId }
        val latestPayment = latestPayments(ids)
        return PageResponse.of(result) { o ->
            val payment = latestPayment[o.id]
            OrderSummary(
                id = o.id,
                orderNumber = o.orderNumber,
                customerName = o.customerNameSnapshot,
                customerPhone = o.customerPhoneSnapshot,
                productName = itemsByOrder[o.id]?.firstOrNull()?.productNameSnapshot ?: "",
                amount = o.totalAmount,
                method = payment?.methodSnapshot,
                paymentStatus = payment?.status,
                status = o.status,
                statusLabel = sellerLabel(o, payment?.status),
                incident = o.incidentReason != null,
                createdAt = o.createdAt,
            )
        }
    }

    @Transactional(readOnly = true)
    fun detail(businessId: UUID, orderId: UUID): OrderDetail {
        val order = load(businessId, orderId)
        val history = payments.findByOrderIdOrderByCreatedAtDesc(order.id)
        val allocation = capacity.reservationFor(businessId, order.id)
        val subscription = subscriptions.forOrder(businessId, order.id)
        val remindersConsent = consents.findByCustomerIdOrderByCreatedAtDesc(order.customerId)
            .firstOrNull { it.purpose == ConsentPurpose.RENEWAL_REMINDERS }?.let { it.granted && it.revokedAt == null } ?: false
        return OrderDetail(
            id = order.id,
            orderNumber = order.orderNumber,
            status = order.status,
            statusLabel = sellerLabel(order, history.firstOrNull()?.status),
            customerId = order.customerId,
            customerName = order.customerNameSnapshot,
            customerPhone = order.customerPhoneSnapshot,
            customerWhatsappUrl = ManualWhatsAppLinkProvider.link(order.customerPhoneSnapshot, "Hola, ${order.customerNameSnapshot}. Te escribo por tu pedido ${order.orderNumber}."),
            amount = order.totalAmount,
            currency = order.currency,
            items = items.findByOrderId(order.id).map {
                OrderItemDetail(it.productId, it.productNameSnapshot, it.productTypeSnapshot, it.unitPriceSnapshot, it.durationSnapshot, it.quantity)
            },
            payments = history.map(::paymentDetail),
            capacityStatus = allocation?.allocationStatus,
            reservedUntil = allocation?.reservedUntil,
            incidentReason = order.incidentReason,
            cancellationReason = order.cancellationReason,
            renewalRemindersConsent = remindersConsent,
            subscriptionId = subscription?.id,
            subscriptionStatus = subscription?.let { SubscriptionLabels.of(it.status) },
            createdAt = order.createdAt,
            paidAt = order.paidAt,
        )
    }

    /** Cancela un pedido no pagado y libera su reserva de cupo. */
    @Transactional
    fun cancel(businessId: UUID, actorId: UUID, orderId: UUID, reason: String): OrderDetail {
        val order = load(businessId, orderId)
        if (order.status != OrderStatus.AWAITING_PAYMENT && order.status != OrderStatus.PAYMENT_REPORTED) {
            throw ApiException.conflict("ORDER_NOT_CANCELLABLE", "Solo se pueden cancelar pedidos sin pago verificado.")
        }
        order.status = OrderStatus.CANCELLED
        order.cancellationReason = reason.trim()
        payments.findByOrderIdOrderByCreatedAtDesc(order.id)
            .filter { it.status == PaymentStatus.PENDING || it.status == PaymentStatus.PENDING_VERIFICATION }
            .forEach { it.status = PaymentStatus.CANCELLED }
        capacity.expireReservationForOrder(businessId, order.id)
        audit.record(
            AuditActions.ORDER_CANCELLED, actorId = actorId, businessId = businessId, entityType = "ORDER", entityId = order.id,
            metadata = mapOf("orderNumber" to order.orderNumber, "reason" to order.cancellationReason),
        )
        return detail(businessId, orderId)
    }

    @Transactional(readOnly = true)
    fun countToVerify(businessId: UUID): Long = orders.countByBusinessIdAndStatus(businessId, OrderStatus.PAYMENT_REPORTED)

    private fun load(businessId: UUID, orderId: UUID) =
        orders.findByIdAndBusinessId(orderId, businessId) ?: throw ApiException.notFound("El pedido no existe.")

    private fun latestPayments(orderIds: List<UUID>): Map<UUID, CustomerPayment> =
        if (orderIds.isEmpty()) emptyMap()
        else payments.findByOrderIdIn(orderIds).groupBy { it.orderId }.mapValues { (_, list) -> list.maxBy { it.createdAt!! } }

    private fun sellerLabel(order: Order, payment: PaymentStatus?) = when (order.status) {
        OrderStatus.PAYMENT_REPORTED -> "Por verificar"
        else -> OrderLabels.status(order.status, payment)
    }

    companion object {
        fun paymentDetail(p: CustomerPayment) = PaymentDetail(
            p.id, p.methodSnapshot, p.holderNameSnapshot, p.receiverPhoneSnapshot, p.amount, p.status, p.transactionReference,
            p.receiptKey != null, p.reportedAt, p.verifiedAt, p.rejectedAt, p.rejectionReason,
        )
    }
}
