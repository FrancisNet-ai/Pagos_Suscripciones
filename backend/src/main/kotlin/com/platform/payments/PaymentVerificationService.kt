package com.platform.payments

import com.platform.capacity.CapacityService
import com.platform.common.audit.AuditActions
import com.platform.common.audit.AuditService
import com.platform.common.error.ApiException
import com.platform.media.MediaStorage
import com.platform.subscriptions.SubscriptionService
import com.platform.orders.OrderItemRepository
import com.platform.orders.OrderKind
import com.platform.renewals.RenewalService
import com.platform.orders.OrderRepository
import com.platform.orders.OrderStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.file.Path
import java.time.Clock
import java.util.UUID

/**
 * Verificación manual de pagos Yape/Plin por el vendedor. El pago se bloquea (`FOR UPDATE`) y la
 * transición solo ocurre desde PENDING_VERIFICATION, por lo que no puede aprobarse dos veces.
 */
@Service
class PaymentVerificationService(
    private val payments: CustomerPaymentRepository,
    private val orders: OrderRepository,
    private val capacity: CapacityService,
    private val items: OrderItemRepository,
    private val subscriptions: SubscriptionService,
    private val renewals: RenewalService,
    private val media: MediaStorage,
    private val audit: AuditService,
    private val clock: Clock,
) {
    @Transactional
    fun verify(businessId: UUID, actorId: UUID, paymentId: UUID): UUID {
        val payment = lockPending(businessId, paymentId)
        val order = orders.findByIdAndBusinessId(payment.orderId, businessId)!!
        // Si el cupo ya no existe (pago tardío), esto falla con PRODUCT_SOLD_OUT y no se aprueba nada.
        capacity.occupyForOrder(businessId, order.id)
        val now = clock.instant()
        payment.status = PaymentStatus.VERIFIED
        payment.verifiedAt = now
        payment.verifiedBy = actorId
        order.status = OrderStatus.PAID
        order.paidAt = now
        order.incidentReason = null
        if (order.kind == OrderKind.RENEWAL) {
            // Renovación: amplía la vigencia de la suscripción existente y conserva su cupo.
            renewals.completeFromPaidOrder(order)
        } else {
            // Pagado no significa entregado: la suscripción queda pendiente de activación.
            subscriptions.createFromPaidOrder(order, items.findByOrderId(order.id))
        }
        audit.record(
            AuditActions.PAYMENT_VERIFIED, actorId = actorId, businessId = businessId, entityType = "PAYMENT", entityId = payment.id,
            metadata = mapOf("orderNumber" to order.orderNumber, "amount" to payment.amount, "method" to payment.methodSnapshot.name, "reference" to payment.transactionReference),
        )
        return order.id
    }

    @Transactional
    fun reject(businessId: UUID, actorId: UUID, paymentId: UUID, reason: String): UUID {
        val payment = lockPending(businessId, paymentId)
        val order = orders.findByIdAndBusinessId(payment.orderId, businessId)!!
        payment.status = PaymentStatus.REJECTED
        payment.rejectedAt = clock.instant()
        payment.rejectionReason = reason.trim()
        // El comprador puede corregir el código y volver a reportar; el cupo se vuelve a reservar si hay espacio.
        order.status = OrderStatus.AWAITING_PAYMENT
        capacity.expireReservationForOrder(businessId, order.id)
        audit.record(
            AuditActions.PAYMENT_REJECTED, actorId = actorId, businessId = businessId, entityType = "PAYMENT", entityId = payment.id,
            metadata = mapOf("orderNumber" to order.orderNumber, "reason" to payment.rejectionReason, "reference" to payment.transactionReference),
        )
        return order.id
    }

    /** Ruta del comprobante; solo para el vendedor dueño del pago. Cada acceso queda auditado. */
    @Transactional
    fun receipt(businessId: UUID, actorId: UUID, paymentId: UUID): Path {
        val payment = payments.findByIdAndBusinessId(paymentId, businessId) ?: throw ApiException.notFound()
        val path = payment.receiptKey?.let { media.resolveReceipt(it) } ?: throw ApiException.notFound("Este pago no tiene comprobante.")
        audit.record(AuditActions.RECEIPT_VIEWED, actorId = actorId, businessId = businessId, entityType = "PAYMENT", entityId = payment.id)
        return path
    }

    private fun lockPending(businessId: UUID, paymentId: UUID): CustomerPayment {
        val payment = payments.lockByIdAndBusinessId(paymentId, businessId) ?: throw ApiException.notFound("El pago no existe.")
        if (payment.status != PaymentStatus.PENDING_VERIFICATION) {
            val message = when (payment.status) {
                PaymentStatus.VERIFIED -> "Este pago ya fue verificado."
                PaymentStatus.REJECTED -> "Este pago ya fue rechazado."
                PaymentStatus.PENDING -> "El comprador aún no reporta este pago."
                else -> "Este pago ya no está pendiente de verificación."
            }
            throw ApiException.conflict("PAYMENT_ALREADY_PROCESSED", message)
        }
        return payment
    }
}
