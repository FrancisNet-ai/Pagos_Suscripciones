package com.platform.orders

import com.platform.capacity.AllocationStatus
import com.platform.payments.PaymentMethodType
import com.platform.payments.PaymentStatus
import com.platform.products.ProductType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** Filtros de la vista de ventas. TO_VERIFY = pagos reportados pendientes de verificación. */
enum class OrderFilter(val statuses: Set<OrderStatus>) {
    ALL(OrderStatus.entries.toSet()),
    TO_VERIFY(setOf(OrderStatus.PAYMENT_REPORTED)),
    AWAITING_PAYMENT(setOf(OrderStatus.AWAITING_PAYMENT)),
    PAID(setOf(OrderStatus.PAID)),
    CLOSED(setOf(OrderStatus.CANCELLED, OrderStatus.EXPIRED)),
}

data class OrderSummary(
    val id: UUID,
    val orderNumber: String,
    val customerName: String,
    val customerPhone: String,
    val productName: String,
    val amount: BigDecimal,
    val method: PaymentMethodType?,
    val paymentStatus: PaymentStatus?,
    val status: OrderStatus,
    val statusLabel: String,
    val incident: Boolean,
    val createdAt: Instant?,
)

data class PaymentDetail(
    val id: UUID,
    val method: PaymentMethodType,
    val holderName: String,
    val receiverPhone: String,
    val amount: BigDecimal,
    val status: PaymentStatus,
    val transactionReference: String?,
    val hasReceipt: Boolean,
    val reportedAt: Instant?,
    val verifiedAt: Instant?,
    val rejectedAt: Instant?,
    val rejectionReason: String?,
)

data class OrderItemDetail(
    val productId: UUID,
    val productName: String,
    val productType: ProductType,
    val unitPrice: BigDecimal,
    val durationDays: Int?,
    val quantity: Int,
)

data class OrderDetail(
    val id: UUID,
    val orderNumber: String,
    val status: OrderStatus,
    val statusLabel: String,
    val customerId: UUID,
    val customerName: String,
    val customerPhone: String,
    val customerWhatsappUrl: String,
    val amount: BigDecimal,
    val currency: String,
    val items: List<OrderItemDetail>,
    val payments: List<PaymentDetail>,
    val capacityStatus: AllocationStatus?,
    val reservedUntil: Instant?,
    val incidentReason: String?,
    val cancellationReason: String?,
    val renewalRemindersConsent: Boolean,
    val subscriptionId: UUID?,
    val subscriptionStatus: String?,
    val createdAt: Instant?,
    val paidAt: Instant?,
)

data class ReasonRequest(
    @field:NotBlank(message = "Indica el motivo")
    @field:Size(min = 5, max = 300, message = "El motivo debe tener entre 5 y 300 caracteres")
    val reason: String?,
)
