package com.platform.storefront

import com.platform.businesses.BusinessRepository
import com.platform.capacity.AllocationStatus
import com.platform.capacity.CapacityService
import com.platform.common.audit.AuditActions
import com.platform.common.audit.AuditService
import com.platform.common.config.AppProperties
import com.platform.common.error.ApiException
import com.platform.common.ratelimit.RateLimiter
import com.platform.common.text.SecureTokens
import com.platform.media.MediaStorage
import com.platform.orders.Order
import com.platform.orders.OrderItemRepository
import com.platform.orders.OrderLabels
import com.platform.orders.OrderRepository
import com.platform.orders.OrderStatus
import com.platform.payments.BusinessPaymentMethodRepository
import com.platform.payments.CustomerPayment
import com.platform.payments.CustomerPaymentRepository
import com.platform.payments.PaymentProviders
import com.platform.payments.PaymentStatus
import com.platform.whatsapp.ManualWhatsAppLinkProvider
import com.platform.whatsapp.OrderMessageData
import com.platform.whatsapp.OrderMessages
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration

/**
 * Seguimiento del pedido por el comprador mediante un token aleatorio (sin cuenta).
 * El token solo da acceso a ESE pedido; en la base se guarda únicamente su hash.
 */
@Service
class PublicOrderService(
    private val orders: OrderRepository,
    private val items: OrderItemRepository,
    private val payments: CustomerPaymentRepository,
    private val paymentMethods: BusinessPaymentMethodRepository,
    private val businesses: BusinessRepository,
    private val providers: PaymentProviders,
    private val capacity: CapacityService,
    private val whatsapp: ManualWhatsAppLinkProvider,
    private val media: MediaStorage,
    private val rateLimiter: RateLimiter,
    private val audit: AuditService,
    private val properties: AppProperties,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun view(token: String): PublicOrderResponse = describe(find(token))

    @Transactional
    fun reportPayment(token: String, rawReference: String, receipt: ByteArray?, ip: String): PublicOrderResponse {
        val tokenHash = SecureTokens.sha256Hex(token)
        rateLimiter.check("report:ip:$ip", limit = 20, window = Duration.ofMinutes(15))
        rateLimiter.check("report:order:$tokenHash", limit = 8, window = Duration.ofHours(1))
        val order = orders.findByPublicTokenHash(tokenHash) ?: throw ApiException.notFound("No encontramos este pedido.")

        when (order.status) {
            OrderStatus.AWAITING_PAYMENT -> Unit
            OrderStatus.PAYMENT_REPORTED -> throw ApiException.conflict("PAYMENT_ALREADY_REPORTED", "Ya reportaste este pago. El vendedor lo está verificando.")
            OrderStatus.PAID -> throw ApiException.conflict("ORDER_ALREADY_PAID", "Este pedido ya fue pagado.")
            OrderStatus.CANCELLED, OrderStatus.EXPIRED ->
                throw ApiException.conflict("ORDER_CLOSED", "Este pedido ya no admite pagos. Escribe al vendedor por WhatsApp si ya transferiste.")
        }
        val reference = rawReference.trim().uppercase()
        val current = payments.findFirstByOrderIdOrderByCreatedAtDesc(order.id)!!
        // Tras un rechazo se registra un nuevo intento para conservar el historial.
        val payment = if (current.status == PaymentStatus.PENDING) current else payments.save(current.newAttempt())
        if (payments.referenceInUse(order.businessId, payment.methodSnapshot, reference, payment.id)) {
            throw ApiException.conflict("DUPLICATE_REFERENCE", "Este código de operación ya fue registrado en otro pedido. Revisa el código de tu comprobante.")
        }

        payment.transactionReference = reference
        payment.receiptKey = receipt?.let { media.storeReceipt(it) }
        // Reportar NUNCA verifica: el vendedor debe confirmar la recepción real del dinero.
        payment.status = PaymentStatus.PENDING_VERIFICATION
        payment.reportedAt = clock.instant()
        order.status = OrderStatus.PAYMENT_REPORTED

        if (!capacity.holdForOrder(order.businessId, order.id, properties.checkout.verificationHold)) {
            order.incidentReason = "El pago se reportó después de vencer la reserva y ya no hay cupos disponibles. Revisa antes de aprobar."
        }
        payments.flush()
        audit.record(
            AuditActions.PAYMENT_REPORTED, businessId = order.businessId, entityType = "PAYMENT", entityId = payment.id,
            metadata = mapOf("orderNumber" to order.orderNumber, "reference" to reference, "receipt" to (receipt != null)), ipAddress = ip,
        )
        return describe(order)
    }

    private fun find(token: String): Order =
        orders.findByPublicTokenHash(SecureTokens.sha256Hex(token)) ?: throw ApiException.notFound("No encontramos este pedido.")

    private fun describe(order: Order): PublicOrderResponse {
        val business = businesses.findById(order.businessId).orElseThrow()
        val item = items.findByOrderId(order.id).first()
        val payment = payments.findFirstByOrderIdOrderByCreatedAtDesc(order.id)
        val reservation = capacity.reservationFor(order.businessId, order.id)
            ?.takeIf { it.allocationStatus == AllocationStatus.RESERVED && order.status == OrderStatus.AWAITING_PAYMENT }
        val statusLabel = OrderLabels.status(order.status, payment?.status)

        val whatsappUrl = business.whatsappNumber?.takeIf { payment != null }?.let { number ->
            val text = OrderMessages.buyerToSeller(
                OrderMessageData(
                    orderNumber = order.orderNumber,
                    customerName = order.customerNameSnapshot,
                    customerPhoneE164 = order.customerPhoneSnapshot,
                    productName = item.productNameSnapshot,
                    amount = order.totalAmount,
                    methodLabel = payment!!.methodSnapshot.label,
                    stage = OrderLabels.messageStage(order.status),
                    statusLabel = statusLabel,
                ),
            )
            whatsapp.prepare(number, text).manualUrl
        }

        return PublicOrderResponse(
            orderNumber = order.orderNumber,
            status = order.status.name,
            statusLabel = statusLabel,
            createdAt = order.createdAt,
            store = PublicOrderStore(business.slug, business.businessName, business.logoUrl, business.primaryColor),
            customerName = order.customerNameSnapshot,
            customerPhoneMasked = OrderLabels.maskPhone(order.customerPhoneSnapshot),
            productName = item.productNameSnapshot,
            productType = item.productTypeSnapshot,
            durationDays = item.durationSnapshot,
            amount = order.totalAmount,
            currency = order.currency,
            payment = payment?.let { publicPayment(it, order) },
            reservedUntil = reservation?.reservedUntil,
            canReportPayment = order.status == OrderStatus.AWAITING_PAYMENT,
            whatsappUrl = whatsappUrl,
        )
    }

    /** Las instrucciones usan la copia guardada en el pedido (y el QR vigente del método si existe). */
    private fun publicPayment(payment: CustomerPayment, order: Order): PublicOrderPayment {
        val method = payment.paymentMethodId?.let { paymentMethods.findById(it).orElse(null) }
        val instructions = providers.forMethod(payment.methodSnapshot).instructions(
            com.platform.payments.BusinessPaymentMethod(
                businessId = payment.businessId,
                method = payment.methodSnapshot,
                holderName = payment.holderNameSnapshot,
                phone = payment.receiverPhoneSnapshot,
                qrImageUrl = method?.qrImageUrl?.takeIf { method.phone == payment.receiverPhoneSnapshot },
            ),
            payment.amount,
            order.orderNumber,
        )
        return PublicOrderPayment(
            status = payment.status.name,
            instructions = instructions,
            transactionReference = payment.transactionReference,
            reportedAt = payment.reportedAt,
            rejectionReason = payment.rejectionReason,
        )
    }
}

/** Nuevo intento de pago con los mismos datos de cobro del pedido. */
fun CustomerPayment.newAttempt() = CustomerPayment(
    businessId = businessId,
    orderId = orderId,
    paymentMethodId = paymentMethodId,
    methodSnapshot = methodSnapshot,
    holderNameSnapshot = holderNameSnapshot,
    receiverPhoneSnapshot = receiverPhoneSnapshot,
    amount = amount,
)
