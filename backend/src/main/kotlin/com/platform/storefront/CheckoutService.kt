package com.platform.storefront

import com.platform.businesses.Business
import com.platform.businesses.BusinessStatus
import com.platform.capacity.CapacityService
import com.platform.common.audit.AuditActions
import com.platform.common.audit.AuditService
import com.platform.common.config.AppProperties
import com.platform.common.error.ApiException
import com.platform.common.ratelimit.RateLimiter
import com.platform.common.text.SecureTokens
import com.platform.common.validation.PeruPhone
import com.platform.customers.ConsentPurpose
import com.platform.customers.Customer
import com.platform.customers.CustomerService
import com.platform.orders.Order
import com.platform.products.Product
import com.platform.orders.OrderItem
import com.platform.orders.OrderItemRepository
import com.platform.orders.OrderNumberService
import com.platform.orders.OrderKind
import com.platform.orders.OrderRepository
import com.platform.payments.BusinessPaymentMethod
import com.platform.payments.BusinessPaymentMethodRepository
import com.platform.payments.CustomerPayment
import com.platform.payments.CustomerPaymentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.ZoneId
import java.util.UUID

/**
 * Compra sin registro. El precio, el producto y el negocio se obtienen SIEMPRE en el servidor;
 * el navegador solo envía qué producto quiere y sus datos de contacto.
 */
@Service
class CheckoutService(
    private val storefront: StorefrontService,
    private val customers: CustomerService,
    private val orders: OrderRepository,
    private val items: OrderItemRepository,
    private val payments: CustomerPaymentRepository,
    private val paymentMethods: BusinessPaymentMethodRepository,
    private val orderNumbers: OrderNumberService,
    private val capacity: CapacityService,
    private val rateLimiter: RateLimiter,
    private val audit: AuditService,
    private val properties: AppProperties,
) {
    @Transactional
    fun createOrder(slug: String, request: CreateOrderRequest, idempotencyKey: UUID?, ip: String): CreateOrderResponse {
        val business = storefront.findBusiness(slug)

        // Reintento del mismo envío (p. ej. conexión móvil inestable): mismo pedido, nuevo enlace.
        if (idempotencyKey != null) {
            orders.findByBusinessIdAndIdempotencyKey(business.id, idempotencyKey)?.let { existing ->
                val token = SecureTokens.newToken()
                existing.publicTokenHash = SecureTokens.sha256Hex(token)
                return CreateOrderResponse(token, existing.orderNumber)
            }
        }

        val phone = PeruPhone.normalize(request.phone) ?: throw ApiException.invalidField("phone", "Ingresa un celular peruano válido de 9 dígitos")
        rateLimiter.check("order:ip:$ip", limit = 15, window = Duration.ofMinutes(15))
        rateLimiter.check("order:phone:${business.id}:$phone", limit = 6, window = Duration.ofHours(1))

        if (business.status != BusinessStatus.ACTIVE) {
            throw ApiException.conflict("STORE_NOT_ACCEPTING_ORDERS", "Esta tienda no está recibiendo pedidos por el momento.")
        }
        val product = storefront.publishedProduct(business, request.productId!!)
        if (!product.available) throw ApiException.conflict("PRODUCT_UNAVAILABLE", "Este producto no está disponible por ahora.")
        val method = paymentMethods.findByBusinessIdAndMethod(business.id, request.paymentMethod!!)?.takeIf { it.enabled }
            ?: throw ApiException.invalidField("paymentMethod", "Este método de pago no está disponible en la tienda")

        val customerName = request.customerName!!.trim().replace(Regex("\\s+"), " ")
        val customer = customers.findOrCreateFromStorefront(business.id, customerName, phone)
        val (order, token) = placeOrder(business, customer.id, customerName, phone, product, method, idempotencyKey, reserveCapacity = true)
        customers.recordConsents(
            customer, order.id,
            mapOf(
                ConsentPurpose.PURCHASE_TERMS to true,
                ConsentPurpose.DATA_PROCESSING to true,
                ConsentPurpose.RENEWAL_REMINDERS to (request.acceptRenewalReminders == true),
            ),
        )
        audit.record(
            AuditActions.ORDER_CREATED, businessId = business.id, entityType = "ORDER", entityId = order.id,
            metadata = mapOf("orderNumber" to order.orderNumber, "amount" to order.totalAmount), ipAddress = ip,
        )
        return CreateOrderResponse(token, order.orderNumber)
    }

    /**
     * Pedido de renovación: mismo cliente y producto, precio vigente del producto. No reserva un
     * cupo nuevo: la suscripción conserva el suyo.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    fun placeRenewalOrder(
        business: Business,
        customer: Customer,
        product: Product,
        method: BusinessPaymentMethod,
        renewalRequestId: UUID,
    ): Pair<Order, String> =
        placeOrder(business, customer.id, customer.fullName, customer.phone, product, method, null, reserveCapacity = false, renewalRequestId = renewalRequestId)

    private fun placeOrder(
        business: Business,
        customerId: UUID,
        customerName: String,
        phone: String,
        product: Product,
        method: BusinessPaymentMethod,
        idempotencyKey: UUID?,
        reserveCapacity: Boolean,
        renewalRequestId: UUID? = null,
    ): Pair<Order, String> {
        val token = SecureTokens.newToken()
        val order = orders.saveAndFlush(
            Order(
                businessId = business.id,
                orderNumber = orderNumbers.next(business.id, ZoneId.of(business.timeZone)),
                customerId = customerId,
                customerNameSnapshot = customerName,
                customerPhoneSnapshot = phone,
                totalAmount = product.price,
                publicTokenHash = SecureTokens.sha256Hex(token),
                idempotencyKey = idempotencyKey,
                kind = if (renewalRequestId != null) OrderKind.RENEWAL else OrderKind.PURCHASE,
                renewalRequestId = renewalRequestId,
            ),
        )
        items.save(
            OrderItem(
                orderId = order.id,
                productId = product.id,
                productNameSnapshot = product.name,
                productTypeSnapshot = product.productType,
                unitPriceSnapshot = product.price,
                durationSnapshot = product.durationDays,
            ),
        )
        // Bloquea el producto y reserva el cupo; si está agotado se revierte todo el pedido.
        if (reserveCapacity) capacity.reserve(business.id, product.id, properties.checkout.reservationHold, order.id)
        payments.save(
            CustomerPayment(
                businessId = business.id,
                orderId = order.id,
                paymentMethodId = method.id,
                methodSnapshot = method.method,
                holderNameSnapshot = method.holderName,
                receiverPhoneSnapshot = method.phone,
                amount = order.totalAmount,
            ),
        )
        return order to token
    }
}
