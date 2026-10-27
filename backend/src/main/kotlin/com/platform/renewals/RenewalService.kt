package com.platform.renewals

import com.platform.businesses.Business
import com.platform.businesses.BusinessRepository
import com.platform.businesses.BusinessStatus
import com.platform.capacity.AllocationStatus
import com.platform.capacity.CapacityAllocationRepository
import com.platform.common.audit.AuditActions
import com.platform.common.audit.AuditService
import com.platform.common.config.AppProperties
import com.platform.common.error.ApiException
import com.platform.common.ratelimit.RateLimiter
import com.platform.common.web.PageResponse
import com.platform.customers.ConsentPurpose
import com.platform.customers.Customer
import com.platform.customers.CustomerConsentRepository
import com.platform.customers.CustomerRepository
import com.platform.notifications.NotificationPreference
import com.platform.notifications.NotificationPreferenceRepository
import com.platform.orders.Order
import com.platform.orders.OrderItemRepository
import com.platform.orders.OrderRepository
import com.platform.orders.OrderStatus
import com.platform.payments.BusinessPaymentMethodRepository
import com.platform.payments.PaymentMethodService
import com.platform.products.ProductRepository
import com.platform.products.ProductType
import com.platform.products.PublicationStatus
import com.platform.storefront.CheckoutService
import com.platform.storefront.CreateOrderResponse
import com.platform.common.text.SecureTokens
import com.platform.subscriptions.CustomerSubscriptionRepository
import com.platform.subscriptions.SubscriptionService
import com.platform.subscriptions.SubscriptionStatus
import com.platform.whatsapp.ManualWhatsAppLinkProvider
import org.springframework.data.domain.PageRequest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * Renovaciones: solicitud por ciclo, respuesta del cliente (Sí / No), pedido de renovación y
 * ampliación de la vigencia solo tras un pago verificado. Responder «sí» nunca renueva por sí solo.
 */
@Service
class RenewalService(
    private val requests: RenewalRequestRepository,
    private val notifications: RenewalNotificationRepository,
    private val renewals: SubscriptionRenewalRepository,
    private val preferences: NotificationPreferenceRepository,
    private val subscriptionRepo: CustomerSubscriptionRepository,
    private val subscriptions: SubscriptionService,
    private val customers: CustomerRepository,
    private val consents: CustomerConsentRepository,
    private val businesses: BusinessRepository,
    private val products: ProductRepository,
    private val paymentMethods: BusinessPaymentMethodRepository,
    private val paymentMethodService: PaymentMethodService,
    private val orders: OrderRepository,
    private val orderItems: OrderItemRepository,
    private val allocations: CapacityAllocationRepository,
    private val checkout: CheckoutService,
    private val tokens: RenewalTokens,
    private val whatsapp: ManualWhatsAppLinkProvider,
    private val rateLimiter: RateLimiter,
    private val audit: AuditService,
    private val jdbc: JdbcTemplate,
    private val properties: AppProperties,
    private val clock: Clock,
) {
    // ---------------------------------------------------------------- tareas programadas

    /** Suscripciones activas que entran en la ventana de aviso y aún no tienen solicitud para su ciclo. */
    fun dueSubscriptions(limit: Int): List<UUID> = jdbc.queryForList(
        """
        select s.id from customer_subscriptions s join businesses b on b.id = s.business_id
        where s.status in ('ACTIVE', 'EXPIRED') and b.status = 'ACTIVE'
          and s.expires_at + make_interval(days => b.renewal_grace_days) >= cast(? as timestamptz)
          and s.expires_at <= cast(? as timestamptz) + make_interval(days => greatest(b.reminder_days_before, 1))
          and not exists (select 1 from renewal_requests r where r.subscription_id = s.id and r.cycle_expires_at = s.expires_at)
        order by s.expires_at limit ?
        """.trimIndent(),
        UUID::class.java, Timestamp.from(clock.instant()), Timestamp.from(clock.instant()), limit,
    ).filterNotNull()

    /** Crea la solicitud y sus avisos. Idempotente: un ciclo nunca genera dos solicitudes. */
    @Transactional
    fun openRequest(subscriptionId: UUID): Boolean {
        val s = subscriptionRepo.lockById(subscriptionId) ?: return false
        if (s.status !in setOf(SubscriptionStatus.ACTIVE, SubscriptionStatus.EXPIRED) || s.expiresAt == null ||
            requests.existsBySubscriptionIdAndCycleExpiresAt(s.id, s.expiresAt!!)) return false
        val business = businesses.findById(s.businessId).orElseThrow()
        val now = clock.instant()
        if (business.status != BusinessStatus.ACTIVE || !withinGrace(s.expiresAt!!, business) ||
            s.expiresAt!!.isAfter(now.plus(Duration.ofDays(maxOf(business.reminderDaysBefore, 1).toLong())))) return false
        val request = RenewalRequest(
            businessId = s.businessId, subscriptionId = s.id, customerId = s.customerId, productId = s.productId,
            cycleExpiresAt = s.expiresAt!!, tokenHash = "pending",
            tokenExpiresAt = s.expiresAt!!.plus(Duration.ofDays(business.renewalGraceDays + 1L)),
        )
        request.tokenHash = tokens.hashFor(request.id)
        requests.save(request)

        val optedOut = isOptedOut(s.customerId)
        val zone = ZoneId.of(business.timeZone)
        if (business.reminderDaysBefore > 0 && s.expiresAt!!.isAfter(now)) {
            schedule(request, NotificationType.REMINDER_BEFORE, maxOf(now, s.expiresAt!!.minus(Duration.ofDays(business.reminderDaysBefore.toLong()))), optedOut)
        }
        if (business.reminderOnDueDay) {
            val dueDay = s.expiresAt!!.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
            schedule(request, NotificationType.REMINDER_DUE_DAY, maxOf(now, dueDay), optedOut)
        }
        return true
    }

    /** Solicitudes cuyo plazo de gracia terminó sin pago: se cierran y no se retiene el cupo indefinidamente. */
    @Transactional
    fun expire(requestId: UUID): Boolean {
        val r = requests.lockById(requestId) ?: return false
        if (r.status != RenewalStatus.OPEN && r.status != RenewalStatus.AWAITING_PAYMENT) return false
        if (clock.instant().isBefore(r.graceExpiresAt) || hasReportedPayment(r)) return false
        r.status = RenewalStatus.EXPIRED
        skipPending(r.id)
        subscriptions.settleUnrenewed(subscriptions.lockEntity(r.businessId, r.subscriptionId))
        audit.record(AuditActions.RENEWAL_EXPIRED, businessId = r.businessId, entityType = "RENEWAL", entityId = r.id)
        return true
    }

    fun staleRequests(limit: Int): List<UUID> = requests.findStale(clock.instant(), limit)

    // ---------------------------------------------------------------- comprador (enlace público)

    @Transactional(readOnly = true)
    fun view(token: String): PublicRenewalResponse = describe(find(token))

    @Transactional
    fun respond(token: String, response: RenewalResponse, ip: String): PublicRenewalResponse {
        rateLimiter.check("renewal:ip:$ip", limit = 30, window = Duration.ofMinutes(15))
        val r = find(token, lock = true)
        if (r.status != RenewalStatus.OPEN && !(r.status == RenewalStatus.AWAITING_PAYMENT && response == RenewalResponse.DECLINED)) {
            throw ApiException.conflict("RENEWAL_CLOSED", "Esta solicitud de renovación ya no admite cambios.")
        }
        val s = subscriptions.lockEntity(r.businessId, r.subscriptionId)
        requireRenewable(r, s)
        if (response == RenewalResponse.DECLINED && hasReportedPayment(r)) {
            throw ApiException.conflict("RENEWAL_PAYMENT_UNDER_REVIEW", "Tu pago está en revisión. Contacta al vendedor antes de cancelar la renovación.")
        }
        r.response = response
        r.respondedAt = clock.instant()
        when (response) {
            RenewalResponse.ACCEPTED -> {
                // Registrar la intención no renueva: se espera el pago verificado.
                r.status = RenewalStatus.AWAITING_PAYMENT
                subscriptions.markRenewalPending(s)
                audit.record(AuditActions.RENEWAL_ACCEPTED, businessId = r.businessId, entityType = "RENEWAL", entityId = r.id, metadata = mapOf("product" to s.productNameSnapshot), ipAddress = ip)
            }
            RenewalResponse.DECLINED -> {
                // Conserva el servicio hasta el fin del periodo pagado; luego el cupo queda por liberar.
                r.status = RenewalStatus.DECLINED
                skipPending(r.id)
                subscriptions.settleUnrenewed(s)
                schedule(r, NotificationType.FAREWELL, clock.instant(), isOptedOut(r.customerId))
                audit.record(AuditActions.RENEWAL_DECLINED, businessId = r.businessId, entityType = "RENEWAL", entityId = r.id, metadata = mapOf("product" to s.productNameSnapshot), ipAddress = ip)
            }
            RenewalResponse.NONE -> throw ApiException.invalidField("response", "Elige si deseas renovar")
        }
        return describe(r)
    }

    /** Crea (o reutiliza) el pedido de renovación. Aceptar implícitamente si aún no respondió. */
    @Transactional
    fun createOrder(token: String, request: RenewalOrderRequest, ip: String): CreateOrderResponse {
        rateLimiter.check("renewal-order:ip:$ip", limit = 15, window = Duration.ofMinutes(15))
        val r = find(token, lock = true)
        if (r.status == RenewalStatus.OPEN) respond(token, RenewalResponse.ACCEPTED, ip)
        if (r.status != RenewalStatus.AWAITING_PAYMENT) {
            throw ApiException.conflict("RENEWAL_CLOSED", "Esta solicitud de renovación ya no admite pagos.")
        }
        requireRenewable(r, subscriptions.lockEntity(r.businessId, r.subscriptionId))
        // Si ya hay un pedido abierto, se entrega un enlace nuevo a ese mismo pedido (sin duplicar).
        r.orderId?.let { orders.findById(it).orElse(null) }
            ?.takeIf { it.status == OrderStatus.AWAITING_PAYMENT || it.status == OrderStatus.PAYMENT_REPORTED }
            ?.let { existing ->
                val newToken = SecureTokens.newToken()
                existing.publicTokenHash = SecureTokens.sha256Hex(newToken)
                return CreateOrderResponse(newToken, existing.orderNumber)
            }

        val business = businesses.findById(r.businessId).orElseThrow()
        if (business.status != BusinessStatus.ACTIVE) throw ApiException.conflict("STORE_NOT_ACCEPTING_ORDERS", "Esta tienda no está recibiendo pagos por el momento.")
        val product = products.findByIdAndBusinessId(r.productId, r.businessId)
            ?.takeIf { it.productType == ProductType.SUBSCRIPTION && it.publicationStatus != PublicationStatus.ARCHIVED && it.available }
            ?: throw ApiException.conflict("PRODUCT_UNAVAILABLE", "Este servicio ya no está disponible para renovar. Escribe al vendedor.")
        val method = paymentMethods.findByBusinessIdAndMethod(r.businessId, request.paymentMethod!!)?.takeIf { it.enabled }
            ?: throw ApiException.invalidField("paymentMethod", "Este método de pago no está disponible")
        val customer = customers.findById(r.customerId).orElseThrow()
        val (order, orderToken) = checkout.placeRenewalOrder(business, customer, product, method, r.id)
        r.orderId = order.id
        audit.record(AuditActions.RENEWAL_ORDER_CREATED, businessId = r.businessId, entityType = "ORDER", entityId = order.id, metadata = mapOf("orderNumber" to order.orderNumber), ipAddress = ip)
        return CreateOrderResponse(orderToken, order.orderNumber)
    }

    @Transactional
    fun optOut(token: String, ip: String): PublicRenewalResponse {
        rateLimiter.check("renewal:ip:$ip", limit = 30, window = Duration.ofMinutes(15))
        val r = find(token)
        setOptOut(r.businessId, r.customerId, true, "CUSTOMER", actorId = null)
        return describe(r)
    }

    // ---------------------------------------------------------------- verificación del pago

    /** Llamado al verificar el pago de un pedido de renovación: amplía la vigencia y cierra la solicitud. */
    @Transactional(propagation = Propagation.MANDATORY)
    fun completeFromPaidOrder(order: Order) {
        val r = requests.lockById(order.renewalRequestId!!) ?: throw ApiException.notFound()
        val s = subscriptions.lockEntity(order.businessId, r.subscriptionId)
        // Un pago reportado antes del cierre puede verificarse después de la gracia.
        // Una solicitud rechazada/cerrada o una suscripción cancelada nunca se reactivan por sorpresa.
        requireRenewable(r, s, checkDeadline = false)
        if (r.status != RenewalStatus.AWAITING_PAYMENT || r.orderId != order.id) {
            throw ApiException.conflict("RENEWAL_CLOSED", "La renovación ya no admite este pago. Revisa la incidencia con el cliente.")
        }
        val item = orderItems.findByOrderId(order.id).first()
        val (previous, next) = subscriptions.extend(s, item.durationSnapshot ?: s.durationDays, order.totalAmount)
        renewals.save(SubscriptionRenewal(order.businessId, s.id, order.id, r.id, previous, next, order.totalAmount, clock.instant()))
        r.status = RenewalStatus.COMPLETED
        r.completedAt = clock.instant()
        r.orderId = order.id
        skipPending(r.id)
        audit.record(
            AuditActions.RENEWAL_COMPLETED, businessId = order.businessId, entityType = "SUBSCRIPTION", entityId = s.id,
            metadata = mapOf("orderNumber" to order.orderNumber, "product" to s.productNameSnapshot),
        )
    }

    // ---------------------------------------------------------------- vendedor

    @Transactional(readOnly = true)
    fun list(businessId: UUID, bucket: RenewalBucket, page: Int, size: Int): PageResponse<RenewalSummary> {
        val result = requests.findByBusinessIdAndStatusInAndResponseOrderByCycleExpiresAtAsc(
            businessId, bucket.statuses, bucket.response, PageRequest.of(maxOf(page, 0), size.coerceIn(1, PageResponse.MAX_SIZE)),
        )
        val subs = subscriptionRepo.findAllById(result.content.map { it.subscriptionId }.toSet()).associateBy { it.id }
        val custs = customers.findAllById(result.content.map { it.customerId }.toSet()).associateBy { it.id }
        return PageResponse.of(result) { r ->
            val s = subs.getValue(r.subscriptionId)
            val c = custs.getValue(r.customerId)
            RenewalSummary(r.id, c.id, c.fullName, c.phone, s.id, s.status, s.productNameSnapshot, r.cycleExpiresAt, r.status, r.response, r.respondedAt, r.orderId, link(r.id))
        }
    }

    /** Avisos listos para enviar manualmente por WhatsApp. */
    @Transactional(readOnly = true)
    fun dueNotifications(businessId: UUID, page: Int, size: Int): PageResponse<NotificationItem> {
        val business = businesses.findById(businessId).orElseThrow()
        val result = notifications.findByBusinessIdAndDeliveryStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
            businessId, DeliveryStatus.PENDING, clock.instant(), PageRequest.of(maxOf(page, 0), size.coerceIn(1, PageResponse.MAX_SIZE)),
        )
        return PageResponse.of(result) { n -> toItem(n, business) }
    }

    @Transactional
    fun markSent(businessId: UUID, actorId: UUID, notificationId: UUID): NotificationItem {
        val n = notifications.lockByIdAndBusinessId(notificationId, businessId) ?: throw ApiException.notFound("El aviso no existe.")
        if (n.deliveryStatus != DeliveryStatus.PENDING) throw ApiException.conflict("NOTIFICATION_PROCESSED", "Este aviso ya fue procesado.")
        if (n.scheduledAt.isAfter(clock.instant())) throw ApiException.conflict("NOTIFICATION_NOT_DUE", "Todavía no corresponde enviar este aviso.")
        if (!canContact(n.customerId)) throw ApiException.conflict("REMINDER_CONSENT_REQUIRED", "No hay autorización vigente para enviar avisos a este cliente.")
        val r = requests.findById(n.renewalRequestId).orElseThrow()
        if (n.notificationType != NotificationType.FAREWELL) {
            requireRenewable(r, subscriptions.entity(businessId, r.subscriptionId))
            if (r.status !in setOf(RenewalStatus.OPEN, RenewalStatus.AWAITING_PAYMENT)) {
                throw ApiException.conflict("RENEWAL_CLOSED", "Esta renovación ya está cerrada.")
            }
        }
        // Solo el vendedor puede afirmar que lo envió: abrir WhatsApp no garantiza el envío.
        n.deliveryStatus = DeliveryStatus.SENT_MANUALLY
        n.sentAt = clock.instant()
        n.sentBy = actorId
        audit.record(AuditActions.NOTIFICATION_SENT, actorId = actorId, businessId = businessId, entityType = "NOTIFICATION", entityId = n.id, metadata = mapOf("type" to n.notificationType.name))
        return toItem(n, businesses.findById(businessId).orElseThrow())
    }

    @Transactional
    fun skip(businessId: UUID, notificationId: UUID) {
        val n = notifications.findByIdAndBusinessId(notificationId, businessId) ?: throw ApiException.notFound("El aviso no existe.")
        if (n.deliveryStatus == DeliveryStatus.PENDING) n.deliveryStatus = DeliveryStatus.SKIPPED
    }

    @Transactional(readOnly = true)
    fun overview(businessId: UUID): RenewalsOverview = RenewalsOverview(
        notificationsDue = notifications.countByBusinessIdAndDeliveryStatusAndScheduledAtLessThanEqual(businessId, DeliveryStatus.PENDING, clock.instant()),
        upcoming = requests.countByBusinessIdAndStatus(businessId, RenewalStatus.OPEN),
        accepted = requests.countByBusinessIdAndStatusInAndResponse(businessId, RenewalBucket.ACCEPTED.statuses, RenewalResponse.ACCEPTED),
        declined = requests.countByBusinessIdAndStatus(businessId, RenewalStatus.DECLINED),
        noResponse = requests.countByBusinessIdAndStatusInAndResponse(businessId, RenewalBucket.NO_RESPONSE.statuses, RenewalResponse.NONE),
        completed = requests.countByBusinessIdAndStatus(businessId, RenewalStatus.COMPLETED),
        capacityReleasePending = allocations.countReleasePending(businessId),
    )

    @Transactional
    fun updateSettings(businessId: UUID, actorId: UUID, body: RenewalSettingsRequest): RenewalSettingsResponse {
        val b = businesses.findById(businessId).orElseThrow()
        b.reminderDaysBefore = body.reminderDaysBefore!!
        b.reminderOnDueDay = body.reminderOnDueDay!!
        b.renewalGraceDays = body.renewalGraceDays!!
        audit.record(AuditActions.RENEWAL_SETTINGS_UPDATED, actorId = actorId, businessId = businessId, entityType = "BUSINESS", entityId = businessId)
        return settings(b)
    }

    @Transactional(readOnly = true)
    fun settings(businessId: UUID) = settings(businesses.findById(businessId).orElseThrow())

    /** Exclusión de avisos: desactiva los pendientes de ese cliente. */
    @Transactional
    fun setOptOut(businessId: UUID, customerId: UUID, optedOut: Boolean, source: String, actorId: UUID?) {
        customers.findByIdAndBusinessId(customerId, businessId) ?: throw ApiException.notFound("El cliente no existe.")
        val pref = preferences.findByCustomerIdAndChannel(customerId)
            ?: preferences.save(NotificationPreference(businessId, customerId, optedOut, source))
        pref.optedOut = optedOut
        pref.source = source
        if (optedOut) {
            jdbc.update(
                "update renewal_notifications set delivery_status = 'SKIPPED', version = version + 1, updated_at = now() where customer_id = ? and delivery_status = 'PENDING'",
                customerId,
            )
        }
        audit.record(AuditActions.NOTIFICATIONS_OPT_OUT, actorId = actorId, businessId = businessId, entityType = "CUSTOMER", entityId = customerId, metadata = mapOf("optedOut" to optedOut, "source" to source))
    }

    @Transactional(readOnly = true)
    fun isOptedOut(customerId: UUID): Boolean = preferences.findByCustomerIdAndChannel(customerId)?.optedOut == true

    @Transactional(readOnly = true)
    fun historyFor(subscriptionId: UUID): List<SubscriptionRenewal> = renewals.findBySubscriptionIdOrderByCreatedAtDesc(subscriptionId)

    @Transactional(readOnly = true)
    fun history(businessId: UUID, subscriptionId: UUID, page: Int, size: Int): PageResponse<RenewalHistoryItem> {
        subscriptions.entity(businessId, subscriptionId)
        return PageResponse.of(renewals.findByBusinessIdAndSubscriptionIdOrderByCreatedAtDesc(
            businessId, subscriptionId, PageRequest.of(maxOf(0, page), size.coerceIn(1, PageResponse.MAX_SIZE)),
        )) { RenewalHistoryItem(it.id, it.orderId, it.previousExpiresAt, it.newExpiresAt, it.amount, it.createdAt) }
    }

    /** Valida el enlace del pedido aunque el trabajo de expiración aún no se haya ejecutado. */
    @Transactional(propagation = Propagation.MANDATORY)
    fun requirePaymentReport(order: Order) {
        val r = requests.lockById(order.renewalRequestId!!) ?: throw ApiException.notFound()
        requireRenewable(r, subscriptions.lockEntity(order.businessId, r.subscriptionId))
        if (r.status != RenewalStatus.AWAITING_PAYMENT || r.orderId != order.id) {
            throw ApiException.conflict("RENEWAL_CLOSED", "Esta renovación ya no admite pagos. Contacta al vendedor si ya transferiste.")
        }
    }

    @Transactional(readOnly = true)
    fun canReportPayment(order: Order): Boolean {
        val r = order.renewalRequestId?.let { requests.findById(it).orElse(null) } ?: return false
        val s = subscriptions.entity(order.businessId, r.subscriptionId)
        return r.orderId == order.id && r.status == RenewalStatus.AWAITING_PAYMENT &&
            clock.instant().isBefore(r.graceExpiresAt) &&
            businesses.findById(r.businessId).orElseThrow().status == BusinessStatus.ACTIVE &&
            s.status in setOf(SubscriptionStatus.ACTIVE, SubscriptionStatus.EXPIRED, SubscriptionStatus.RENEWAL_PENDING)
    }

    // ---------------------------------------------------------------- utilidades

    fun link(requestId: UUID) = "${properties.publicBaseUrl.trimEnd('/')}/renovar/${tokens.tokenFor(requestId)}"

    private fun settings(b: Business) = RenewalSettingsResponse(b.reminderDaysBefore, b.reminderOnDueDay, b.renewalGraceDays)

    private fun withinGrace(expiresAt: Instant, business: Business): Boolean =
        clock.instant().isBefore(expiresAt.plus(Duration.ofDays(business.renewalGraceDays.toLong())))

    private fun hasReportedPayment(r: RenewalRequest): Boolean =
        r.orderId?.let { orders.findById(it).orElse(null)?.status == OrderStatus.PAYMENT_REPORTED } == true

    private fun requireRenewable(r: RenewalRequest, s: com.platform.subscriptions.CustomerSubscription, checkDeadline: Boolean = true) {
        val business = businesses.findById(r.businessId).orElseThrow()
        if (business.status != BusinessStatus.ACTIVE || s.status !in setOf(SubscriptionStatus.ACTIVE, SubscriptionStatus.EXPIRED, SubscriptionStatus.RENEWAL_PENDING) ||
            (checkDeadline && !clock.instant().isBefore(r.graceExpiresAt))) {
            throw ApiException.conflict("RENEWAL_CLOSED", "Esta renovación ya no admite cambios. Contacta al vendedor si ya transferiste.")
        }
    }

    private fun canContact(customerId: UUID): Boolean = !isOptedOut(customerId) &&
        consents.findByCustomerIdOrderByCreatedAtDesc(customerId).firstOrNull { it.purpose == ConsentPurpose.RENEWAL_REMINDERS }
            ?.let { it.granted && it.revokedAt == null } == true

    private fun schedule(r: RenewalRequest, type: NotificationType, at: Instant, optedOut: Boolean) {
        if (notifications.existsByRenewalRequestIdAndNotificationType(r.id, type)) return
        notifications.save(
            RenewalNotification(r.businessId, r.id, r.customerId, type, at, deliveryStatus = if (optedOut) DeliveryStatus.SKIPPED else DeliveryStatus.PENDING),
        )
    }

    private fun skipPending(requestId: UUID) {
        notifications.findByRenewalRequestId(requestId)
            .filter { it.deliveryStatus == DeliveryStatus.PENDING && it.notificationType != NotificationType.FAREWELL }
            .forEach { it.deliveryStatus = DeliveryStatus.SKIPPED }
    }

    private fun find(token: String, lock: Boolean = false): RenewalRequest {
        val hash = SecureTokens.sha256Hex(token)
        val r = (if (lock) requests.lockByTokenHash(hash) else requests.findByTokenHash(hash))
            ?: throw ApiException.notFound("Este enlace de renovación no es válido.")
        if (!clock.instant().isBefore(r.tokenExpiresAt)) {
            throw ApiException(org.springframework.http.HttpStatus.GONE, "RENEWAL_LINK_EXPIRED", "Este enlace de renovación venció. Escribe al vendedor para renovar.")
        }
        return r
    }

    private fun describe(r: RenewalRequest): PublicRenewalResponse {
        val business = businesses.findById(r.businessId).orElseThrow()
        val s = subscriptions.entity(r.businessId, r.subscriptionId)
        val customer = customers.findById(r.customerId).orElseThrow()
        val product = products.findById(r.productId).orElseThrow()
        val available = business.status == BusinessStatus.ACTIVE && product.productType == ProductType.SUBSCRIPTION && product.publicationStatus != PublicationStatus.ARCHIVED && product.available
        val renewable = s.status in setOf(SubscriptionStatus.ACTIVE, SubscriptionStatus.EXPIRED, SubscriptionStatus.RENEWAL_PENDING) && clock.instant().isBefore(r.graceExpiresAt)
        return PublicRenewalResponse(
            status = r.status,
            response = r.response,
            store = PublicRenewalStore(business.slug, business.businessName, business.logoUrl, business.primaryColor),
            customerFirstName = customer.fullName.trim().split(Regex("\\s+")).first(),
            productName = s.productNameSnapshot,
            durationDays = product.durationDays,
            price = product.price,
            currentExpiresAt = s.expiresAt!!,
            canRespond = renewable && business.status == BusinessStatus.ACTIVE && r.status == RenewalStatus.OPEN,
            canPay = renewable && available && (r.status == RenewalStatus.OPEN || r.status == RenewalStatus.AWAITING_PAYMENT),
            paymentMethods = if (available) paymentMethodService.enabledMethods(business.id).map { PublicMethod(it.method, it.method.label) } else emptyList(),
            farewellMessage = if (r.status == RenewalStatus.DECLINED) RenewalMessages.farewell(customer.fullName, business.businessName) else null,
            optedOut = isOptedOut(customer.id),
        )
    }

    private fun toItem(n: RenewalNotification, business: Business): NotificationItem {
        val r = requests.findById(n.renewalRequestId).orElseThrow()
        val s = subscriptionRepo.findById(r.subscriptionId).orElseThrow()
        val c: Customer = customers.findById(n.customerId).orElseThrow()
        val zone = ZoneId.of(business.timeZone)
        val dueDate = r.cycleExpiresAt.atZone(zone).toLocalDate()
        val today = clock.instant().atZone(zone).toLocalDate()
        val message = when (n.notificationType) {
            NotificationType.FAREWELL -> RenewalMessages.farewell(c.fullName, business.businessName)
            else -> RenewalMessages.reminder(
                c.fullName, s.productNameSnapshot, business.businessName, r.cycleExpiresAt, zone,
                dueToday = dueDate == today,
                link = link(r.id),
                overdue = dueDate.isBefore(today),
            )
        }
        val consent = canContact(c.id)
        return NotificationItem(
            id = n.id, type = n.notificationType,
            typeLabel = when (n.notificationType) {
                NotificationType.REMINDER_BEFORE -> "Recordatorio previo"
                NotificationType.REMINDER_DUE_DAY -> if (dueDate.isBefore(today)) "Vencimiento pendiente de renovar" else "Vence hoy"
                NotificationType.FAREWELL -> "Despedida"
            },
            customerId = c.id, customerName = c.fullName, customerPhone = c.phone, productName = s.productNameSnapshot,
            scheduledAt = n.scheduledAt, message = message, whatsappUrl = if (consent) whatsapp.prepare(c.phone, message).manualUrl else null, consent = consent,
        )
    }
}
