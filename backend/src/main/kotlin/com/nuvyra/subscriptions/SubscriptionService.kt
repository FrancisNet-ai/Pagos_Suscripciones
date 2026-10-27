package com.nuvyra.subscriptions

import com.nuvyra.capacity.CapacityService
import com.nuvyra.common.audit.AuditActions
import com.nuvyra.common.audit.AuditService
import com.nuvyra.common.error.ApiException
import com.nuvyra.common.web.PageResponse
import com.nuvyra.customers.Customer
import com.nuvyra.customers.CustomerRepository
import com.nuvyra.orders.Order
import com.nuvyra.orders.OrderItem
import com.nuvyra.products.ProductRepository
import com.nuvyra.products.ProductService
import com.nuvyra.products.ProductType
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.math.RoundingMode
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

@Service
class SubscriptionService(
    private val subscriptions: CustomerSubscriptionRepository,
    private val customers: CustomerRepository,
    private val products: ProductRepository,
    private val capacity: CapacityService,
    private val audit: AuditService,
    private val clock: Clock,
) {
    /**
     * Al verificarse el pago de una suscripción se crea pendiente de activación: el pago no
     * prueba la entrega. El vendedor registra la fecha real de inicio al activarla.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    fun createFromPaidOrder(order: Order, items: List<OrderItem>) {
        items.filter { it.productTypeSnapshot == ProductType.SUBSCRIPTION && it.durationSnapshot != null }.forEach { item ->
            if (subscriptions.findByOrderIdAndBusinessId(order.id, order.businessId) != null) return@forEach
            val subscription = subscriptions.save(
                CustomerSubscription(
                    businessId = order.businessId,
                    customerId = order.customerId,
                    productId = item.productId,
                    orderId = order.id,
                    origin = SubscriptionOrigin.ORDER,
                    productNameSnapshot = item.productNameSnapshot,
                    durationDays = item.durationSnapshot!!,
                    contractedPrice = item.unitPriceSnapshot,
                    status = SubscriptionStatus.PENDING_ACTIVATION,
                    paidAt = order.paidAt,
                ),
            )
            capacity.linkOrderToSubscription(order.businessId, order.id, subscription.id)
            audit.record(AuditActions.SUBSCRIPTION_CREATED, businessId = order.businessId, entityType = "SUBSCRIPTION", entityId = subscription.id, metadata = mapOf("orderNumber" to order.orderNumber))
        }
    }

    @Transactional
    fun activate(businessId: UUID, actorId: UUID, subscriptionId: UUID, startAt: Instant?): SubscriptionResponse {
        val s = load(businessId, subscriptionId)
        if (s.status != SubscriptionStatus.PENDING_ACTIVATION) {
            throw ApiException.conflict("SUBSCRIPTION_NOT_PENDING", "Esta suscripción ya fue activada o cerrada.")
        }
        val now = clock.instant()
        val start = startAt ?: now
        if (start.isAfter(now.plus(Duration.ofDays(30))) || start.isBefore(now.minus(Duration.ofDays(365)))) {
            throw ApiException.invalidField("startAt", "La fecha de inicio debe estar entre hace un año y los próximos 30 días")
        }
        s.startAt = start
        s.expiresAt = start.plus(Duration.ofDays(s.durationDays.toLong()))
        s.status = if (s.expiresAt!!.isAfter(now)) SubscriptionStatus.ACTIVE else SubscriptionStatus.EXPIRED
        audit.record(AuditActions.SUBSCRIPTION_ACTIVATED, actorId = actorId, businessId = businessId, entityType = "SUBSCRIPTION", entityId = s.id, metadata = mapOf("product" to s.productNameSnapshot))
        return toResponse(s)
    }

    /** Cancela el servicio. El cupo queda pendiente de liberar hasta que se retire el acceso. */
    @Transactional
    fun cancel(businessId: UUID, actorId: UUID, subscriptionId: UUID, reason: String): SubscriptionResponse {
        val s = load(businessId, subscriptionId)
        if (s.status !in setOf(SubscriptionStatus.PENDING_ACTIVATION, SubscriptionStatus.ACTIVE, SubscriptionStatus.RENEWAL_PENDING, SubscriptionStatus.EXPIRED)) {
            throw ApiException.conflict("SUBSCRIPTION_CLOSED", "Esta suscripción ya está cerrada.")
        }
        s.status = SubscriptionStatus.CANCELLED
        s.cancelledAt = clock.instant()
        s.cancellationReason = reason.trim()
        capacity.markReleasePending(businessId, s.id)
        audit.record(AuditActions.SUBSCRIPTION_CANCELLED, actorId = actorId, businessId = businessId, entityType = "SUBSCRIPTION", entityId = s.id, metadata = mapOf("reason" to s.cancellationReason))
        return toResponse(s)
    }

    /** El vendedor confirma que retiró el acceso en el servicio externo: el cupo vuelve a estar disponible. */
    @Transactional
    fun confirmAccessRemoved(businessId: UUID, actorId: UUID, subscriptionId: UUID): SubscriptionResponse {
        val s = load(businessId, subscriptionId)
        if (s.status !in setOf(SubscriptionStatus.EXPIRED, SubscriptionStatus.CANCELLED)) {
            throw ApiException.conflict("ACCESS_STILL_VALID", "Solo puedes liberar el cupo de suscripciones vencidas o canceladas.")
        }
        if (s.status == SubscriptionStatus.EXPIRED) s.status = SubscriptionStatus.ENDED
        if (capacity.releaseForSubscription(businessId, s.id)) {
            audit.record(AuditActions.CAPACITY_RELEASED, actorId = actorId, businessId = businessId, entityType = "SUBSCRIPTION", entityId = s.id, metadata = mapOf("product" to s.productNameSnapshot))
        }
        return toResponse(s)
    }

    /**
     * Registra una suscripción que el cliente ya tenía antes de usar la plataforma. No genera
     * pedido ni pago: su importe es histórico y no cuenta como ingreso confirmado.
     */
    @Transactional
    fun registerManual(businessId: UUID, actorId: UUID, customer: Customer, request: ManualSubscriptionRequest, zone: ZoneId): SubscriptionResponse {
        val product = products.findByIdAndBusinessId(request.productId!!, businessId) ?: throw ApiException.notFound("El producto no existe.")
        val start = request.startDate!!.atStartOfDay(zone).toInstant()
        val now = clock.instant()
        if (start.isAfter(now.plus(Duration.ofDays(30)))) throw ApiException.invalidField("startDate", "La fecha de inicio no puede estar tan adelantada")
        val expires = start.plus(Duration.ofDays(request.durationDays!!.toLong()))
        when (request.status!!) {
            ManualStatus.ACTIVE -> if (!expires.isAfter(now)) {
                throw ApiException.invalidField("status", "Con esas fechas la suscripción ya venció. Márcala como vencida o corrige la duración.")
            }
            ManualStatus.EXPIRED -> if (expires.isAfter(now)) {
                throw ApiException.invalidField("status", "Con esas fechas la suscripción sigue vigente. Márcala como activa.")
            }
            ManualStatus.CANCELLED -> Unit
        }
        if (subscriptions.duplicateManual(businessId, customer.id, product.id, start)) {
            throw ApiException.conflict("DUPLICATE_SUBSCRIPTION", "Este cliente ya tiene registrada esa suscripción con la misma fecha de inicio.")
        }
        val s = subscriptions.save(
            CustomerSubscription(
                businessId = businessId,
                customerId = customer.id,
                productId = product.id,
                orderId = null,
                origin = SubscriptionOrigin.MANUAL,
                productNameSnapshot = product.name,
                durationDays = request.durationDays,
                contractedPrice = request.contractedPrice!!.setScale(2, RoundingMode.HALF_UP),
                status = when (request.status) {
                    ManualStatus.ACTIVE -> SubscriptionStatus.ACTIVE
                    ManualStatus.EXPIRED -> SubscriptionStatus.EXPIRED
                    ManualStatus.CANCELLED -> SubscriptionStatus.CANCELLED
                },
                startAt = start,
                expiresAt = expires,
                cancelledAt = if (request.status == ManualStatus.CANCELLED) now else null,
                notes = request.notes?.trim()?.ifEmpty { null },
            ),
        )
        // Un cliente activo ocupa un cupo, igual que si hubiera comprado en la tienda.
        if (request.status == ManualStatus.ACTIVE) capacity.occupyForSubscription(businessId, product.id, s.id)
        audit.record(
            AuditActions.SUBSCRIPTION_REGISTERED, actorId = actorId, businessId = businessId, entityType = "SUBSCRIPTION", entityId = s.id,
            metadata = mapOf("product" to product.name, "customer" to customer.fullName, "status" to request.status.name),
        )
        return toResponse(s, customer)
    }

    @Transactional(readOnly = true)
    fun list(businessId: UUID, filter: SubscriptionFilter, query: String?, expiringDays: Long, page: Int, size: Int): PageResponse<SubscriptionResponse> {
        val statuses = when (filter) {
            SubscriptionFilter.ALL -> SubscriptionStatus.entries
            SubscriptionFilter.PENDING_ACTIVATION -> listOf(SubscriptionStatus.PENDING_ACTIVATION)
            SubscriptionFilter.ACTIVE, SubscriptionFilter.EXPIRING -> listOf(SubscriptionStatus.ACTIVE, SubscriptionStatus.RENEWAL_PENDING)
            SubscriptionFilter.RENEWAL -> listOf(SubscriptionStatus.RENEWAL_PENDING)
            SubscriptionFilter.EXPIRED -> listOf(SubscriptionStatus.EXPIRED)
            SubscriptionFilter.CLOSED -> listOf(SubscriptionStatus.CANCELLED, SubscriptionStatus.ENDED)
            SubscriptionFilter.RELEASE_PENDING -> SubscriptionStatus.entries
        }
        val sort = when (filter) {
            SubscriptionFilter.ACTIVE, SubscriptionFilter.EXPIRING, SubscriptionFilter.EXPIRED, SubscriptionFilter.RENEWAL,
            SubscriptionFilter.RELEASE_PENDING -> Sort.by("expiresAt")
            else -> Sort.by(Sort.Direction.DESC, "createdAt")
        }
        val result = subscriptions.search(
            businessId, statuses, filter == SubscriptionFilter.EXPIRING, clock.instant().plus(Duration.ofDays(expiringDays.coerceIn(1, 60))),
            filter == SubscriptionFilter.RELEASE_PENDING, ProductService.likePattern(query), PageRequest.of(maxOf(page, 0), size.coerceIn(1, PageResponse.MAX_SIZE), sort),
        )
        val customerById = customers.findAllById(result.content.map { it.customerId }.toSet()).associateBy { it.id }
        return PageResponse.of(result) { toResponse(it, customerById.getValue(it.customerId)) }
    }

    @Transactional(readOnly = true)
    fun forCustomer(businessId: UUID, customer: Customer): List<SubscriptionResponse> =
        subscriptions.findByCustomerIdAndBusinessIdOrderByCreatedAtDesc(customer.id, businessId).map { toResponse(it, customer) }

    @Transactional(readOnly = true)
    fun forOrder(businessId: UUID, orderId: UUID): CustomerSubscription? = subscriptions.findByOrderIdAndBusinessId(orderId, businessId)

    @Transactional(readOnly = true)
    fun forRenewalRequest(businessId: UUID, renewalRequestId: UUID): CustomerSubscription? =
        subscriptions.subscriptionIdForRenewal(renewalRequestId)?.let { subscriptions.findByIdAndBusinessId(it, businessId) }

    @Transactional(readOnly = true)
    fun get(businessId: UUID, subscriptionId: UUID): SubscriptionResponse = toResponse(load(businessId, subscriptionId))

    /** Marca como vencidas las activas cuyo periodo terminó. El cupo NO se libera automáticamente. */
    @Transactional
    fun expire(subscriptionId: UUID): Boolean {
        val s = subscriptions.findById(subscriptionId).orElse(null) ?: return false
        if (s.status != SubscriptionStatus.ACTIVE || s.expiresAt?.isAfter(clock.instant()) != false) return false
        s.status = SubscriptionStatus.EXPIRED
        capacity.markReleasePending(s.businessId, s.id)
        audit.record(AuditActions.SUBSCRIPTION_EXPIRED, businessId = s.businessId, entityType = "SUBSCRIPTION", entityId = s.id, metadata = mapOf("product" to s.productNameSnapshot))
        return true
    }

    /** El cliente aceptó renovar: la suscripción queda en renovación y conserva su cupo. */
    @Transactional(propagation = Propagation.MANDATORY)
    fun markRenewalPending(s: CustomerSubscription) {
        if (s.status == SubscriptionStatus.ACTIVE || s.status == SubscriptionStatus.EXPIRED) {
            s.status = SubscriptionStatus.RENEWAL_PENDING
            capacity.holdForRenewalIntent(s.businessId, s.id)
        }
    }

    /**
     * Renovación no concretada (rechazo o plazo de gracia vencido). Si el periodo pagado sigue
     * vigente, el servicio continúa hasta su fecha; si ya terminó, vence y el cupo queda por liberar.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    fun settleUnrenewed(s: CustomerSubscription) {
        if (s.status != SubscriptionStatus.RENEWAL_PENDING && s.status != SubscriptionStatus.ACTIVE) return
        if (s.expiresAt!!.isAfter(clock.instant())) {
            s.status = SubscriptionStatus.ACTIVE
        } else {
            s.status = SubscriptionStatus.EXPIRED
            capacity.markReleasePending(s.businessId, s.id)
        }
    }

    /**
     * Amplía la vigencia tras un pago de renovación verificado. Si renueva antes de vencer, el
     * nuevo periodo empieza al terminar el actual; si ya venció, empieza ahora.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    fun extend(s: CustomerSubscription, durationDays: Int, amount: java.math.BigDecimal): Pair<Instant, Instant> {
        val now = clock.instant()
        val previous = s.expiresAt!!
        val base = if (previous.isAfter(now)) previous else now
        val next = base.plus(Duration.ofDays(durationDays.toLong()))
        capacity.keepForRenewal(s.businessId, s.productId, s.id)
        // Renovación después del vencimiento: hubo un corte, el nuevo periodo empieza hoy.
        if (!previous.isAfter(now)) s.startAt = now
        s.expiresAt = next
        s.durationDays = durationDays
        s.contractedPrice = amount
        s.lastRenewedAt = now
        s.status = SubscriptionStatus.ACTIVE
        return previous to next
    }

    @Transactional(readOnly = true)
    fun entity(businessId: UUID, subscriptionId: UUID): CustomerSubscription = load(businessId, subscriptionId)

    private fun load(businessId: UUID, id: UUID) =
        subscriptions.findByIdAndBusinessId(id, businessId) ?: throw ApiException.notFound("La suscripción no existe.")

    private fun toResponse(s: CustomerSubscription, customer: Customer = customers.findById(s.customerId).orElseThrow()): SubscriptionResponse {
        val now = clock.instant()
        return SubscriptionResponse(
            id = s.id,
            customerId = customer.id,
            customerName = customer.fullName,
            customerPhone = customer.phone,
            productId = s.productId,
            productName = s.productNameSnapshot,
            orderId = s.orderId,
            origin = s.origin,
            status = s.status,
            statusLabel = SubscriptionLabels.of(s.status),
            durationDays = s.durationDays,
            contractedPrice = s.contractedPrice,
            paidAt = s.paidAt,
            startAt = s.startAt,
            expiresAt = s.expiresAt,
            daysRemaining = s.expiresAt?.takeIf { s.status == SubscriptionStatus.ACTIVE || s.status == SubscriptionStatus.RENEWAL_PENDING }
                ?.let { Math.ceilDiv(Duration.between(now, it).toHours(), 24L).coerceAtLeast(0) },
            capacityStatus = capacity.allocationForSubscription(s.businessId, s.id)?.allocationStatus,
            cancellationReason = s.cancellationReason,
            notes = s.notes,
            createdAt = s.createdAt,
        )
    }
}
