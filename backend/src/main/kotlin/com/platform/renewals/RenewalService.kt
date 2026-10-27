package com.platform.renewals

import com.platform.businesses.Business
import com.platform.businesses.BusinessRepository
import com.platform.saas.EntitlementService
import com.platform.saas.PlanFeature
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
    private val entitlements: EntitlementService,
    private val whatsAppIntegrations: com.platform.whatsapp.WhatsAppIntegrationService,
    private val jdbc: JdbcTemplate,
    private val properties: AppProperties,
    private val clock: Clock,
) {
    // ---------------------------------------------------------------- tareas programadas

    /** Suscripciones activas que entran en la ventana de aviso y aún no tienen solicitud para su ciclo. */
    fun dueSubscriptions(limit: Int): List<UUID> = jdbc.queryForList(
        """
        select s.id from customer_subscriptions s join businesses b on b.id = s.business_id
        where s.status = 'ACTIVE' and ${EntitlementService.entitledSql("sp.feature_reminders")}
          and s.expires_at <= cast(? as timestamptz) + make_interval(days => greatest(b.reminder_days_before, 1))
          and not exists (select 1 from renewal_requests r where r.subscription_id = s.id and r.cycle_expires_at = s.expires_at)
        order by s.expires_at limit ?
        """.trimIndent(),
        UUID::class.java, Timestamp.from(clock.instant()), Timestamp.from(clock.instant()), limit,
    ).filterNotNull()

    /** Crea la solicitud y sus avisos. Idempotente: un ciclo nunca genera dos solicitudes. */
    @Transactional
    fun openRequest(subscriptionId: UUID): Boolean {
        val s = subscriptionRepo.findById(subscriptionId).orElse(null) ?: return false
        if (s.status != SubscriptionStatus.ACTIVE || requests.existsBySubscriptionIdAndCycleExpiresAt(s.id, s.expiresAt!!)) return false
        val business = businesses.findById(s.businessId).orElseThrow()
        val now = clock.instant()
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
        val r = requests.findById(requestId).orElse(null) ?: return false
        if (r.status != RenewalStatus.OPEN && r.status != RenewalStatus.AWAITING_PAYMENT) return false
        r.status = RenewalStatus.EXPIRED
        skipPending(r.id)
        subscriptions.settleUnrenewed(subscriptions.entity(r.businessId, r.subscriptionId))
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
        if (!acceptsResponse(r, response)) throw ApiException.conflict("RENEWAL_CLOSED", "Esta solicitud de renovación ya no admite cambios.")
        applyResponse(r, response, ip, channel = "WEB")
        return describe(r)
    }

    /**
     * Respuesta «Sí» o «No» desde un botón de WhatsApp (webhook firmado). Devuelve la solicitud si se
     * aplicó, o `null` si ya no admite cambios. Igual que en la web: decir que sí no es pagar.
     */
    @Transactional
    fun respondViaWhatsApp(businessId: UUID, requestId: UUID, response: RenewalResponse): RenewalRequest? {
        val r = requests.lockById(requestId)?.takeIf { it.businessId == businessId } ?: return null
        if (!clock.instant().isBefore(r.tokenExpiresAt) || !acceptsResponse(r, response)) return null
        applyResponse(r, response, ip = null, channel = "WHATSAPP")
        return r
    }

    private fun acceptsResponse(r: RenewalRequest, response: RenewalResponse) =
        r.status == RenewalStatus.OPEN || (r.status == RenewalStatus.AWAITING_PAYMENT && response == RenewalResponse.DECLINED)

    private fun applyResponse(r: RenewalRequest, response: RenewalResponse, ip: String?, channel: String) {
        val s = subscriptions.entity(r.businessId, r.subscriptionId)
        r.response = response
        r.respondedAt = clock.instant()
        when (response) {
            RenewalResponse.ACCEPTED -> {
                // Registrar la intención no renueva: se espera el pago verificado.
                r.status = RenewalStatus.AWAITING_PAYMENT
                subscriptions.markRenewalPending(s)
                audit.record(AuditActions.RENEWAL_ACCEPTED, businessId = r.businessId, entityType = "RENEWAL", entityId = r.id, metadata = mapOf("product" to s.productNameSnapshot, "channel" to channel), ipAddress = ip)
            }
            RenewalResponse.DECLINED -> {
                // Conserva el servicio hasta el fin del periodo pagado; luego el cupo queda por liberar.
                r.status = RenewalStatus.DECLINED
                skipPending(r.id)
                subscriptions.settleUnrenewed(s)
                schedule(r, NotificationType.FAREWELL, clock.instant(), isOptedOut(r.customerId))
                audit.record(AuditActions.RENEWAL_DECLINED, businessId = r.businessId, entityType = "RENEWAL", entityId = r.id, metadata = mapOf("product" to s.productNameSnapshot, "channel" to channel), ipAddress = ip)
            }
            RenewalResponse.NONE -> throw ApiException.invalidField("response", "Elige si deseas renovar")
        }
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
        // Si ya hay un pedido abierto, se entrega un enlace nuevo a ese mismo pedido (sin duplicar).
        r.orderId?.let { orders.findById(it).orElse(null) }
            ?.takeIf { it.status == OrderStatus.AWAITING_PAYMENT || it.status == OrderStatus.PAYMENT_REPORTED }
            ?.let { existing ->
                val newToken = SecureTokens.newToken()
                existing.publicTokenHash = SecureTokens.sha256Hex(newToken)
                return CreateOrderResponse(newToken, existing.orderNumber)
            }

        val business = businesses.findById(r.businessId).orElseThrow()
        if (!entitlements.canSell(business)) throw ApiException.conflict("STORE_NOT_ACCEPTING_ORDERS", "Esta tienda no está recibiendo pagos por el momento.")
        val product = products.findByIdAndBusinessId(r.productId, r.businessId)
            ?.takeIf { it.publicationStatus != PublicationStatus.ARCHIVED && it.available }
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
        val r = requests.findById(order.renewalRequestId!!).orElseThrow()
        val s = subscriptions.entity(order.businessId, r.subscriptionId)
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
        val result = requests.findByBusinessIdAndStatusInOrderByCycleExpiresAtAsc(
            businessId, bucket.statuses, PageRequest.of(maxOf(page, 0), size.coerceIn(1, PageResponse.MAX_SIZE)),
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
        val result = notifications.findByBusinessIdAndDeliveryStatusInAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
            businessId, ACTIONABLE, clock.instant(), PageRequest.of(maxOf(page, 0), size.coerceIn(1, PageResponse.MAX_SIZE)),
        )
        return PageResponse.of(result) { n -> toItem(n, business) }
    }

    @Transactional
    fun markSent(businessId: UUID, actorId: UUID, notificationId: UUID): NotificationItem {
        val n = notifications.findByIdAndBusinessId(notificationId, businessId) ?: throw ApiException.notFound("El aviso no existe.")
        if (!n.actionable) throw ApiException.conflict("NOTIFICATION_PROCESSED", "Este aviso ya fue procesado.")
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
        if (n.actionable) n.deliveryStatus = DeliveryStatus.SKIPPED
    }

    @Transactional(readOnly = true)
    fun overview(businessId: UUID): RenewalsOverview = RenewalsOverview(
        notificationsDue = notifications.countByBusinessIdAndDeliveryStatusInAndScheduledAtLessThanEqual(businessId, ACTIONABLE, clock.instant()),
        upcoming = requests.countByBusinessIdAndStatus(businessId, RenewalStatus.OPEN),
        accepted = requests.countByBusinessIdAndStatus(businessId, RenewalStatus.AWAITING_PAYMENT),
        declined = requests.countByBusinessIdAndStatus(businessId, RenewalStatus.DECLINED),
        noResponse = requests.countByBusinessIdAndStatus(businessId, RenewalStatus.EXPIRED),
        completed = requests.countByBusinessIdAndStatus(businessId, RenewalStatus.COMPLETED),
        capacityReleasePending = allocations.countReleasePending(businessId),
    )

    @Transactional
    fun updateSettings(businessId: UUID, actorId: UUID, body: RenewalSettingsRequest): RenewalSettingsResponse {
        entitlements.requireFeature(businessId, PlanFeature.REMINDERS)
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
        val pref = preferences.findByCustomerIdAndChannel(customerId)
            ?: preferences.save(NotificationPreference(businessId, customerId, optedOut, source))
        pref.optedOut = optedOut
        pref.source = source
        if (optedOut) {
            jdbc.update(
                "update renewal_notifications set delivery_status = 'SKIPPED', version = version + 1, updated_at = now() where customer_id = ? and delivery_status in ('PENDING', 'FAILED')",
                customerId,
            )
        }
        audit.record(AuditActions.NOTIFICATIONS_OPT_OUT, actorId = actorId, businessId = businessId, entityType = "CUSTOMER", entityId = customerId, metadata = mapOf("optedOut" to optedOut, "source" to source))
    }

    @Transactional(readOnly = true)
    fun isOptedOut(customerId: UUID): Boolean = preferences.findByCustomerIdAndChannel(customerId)?.optedOut == true

    @Transactional(readOnly = true)
    fun historyFor(subscriptionId: UUID): List<SubscriptionRenewal> = renewals.findBySubscriptionIdOrderByCreatedAtDesc(subscriptionId)

    // ---------------------------------------------------------------- utilidades

    fun link(requestId: UUID) = "${properties.publicBaseUrl.trimEnd('/')}/renovar/${tokens.tokenFor(requestId)}"

    private fun settings(b: Business) = RenewalSettingsResponse(b.reminderDaysBefore, b.reminderOnDueDay, b.renewalGraceDays)

    private fun schedule(r: RenewalRequest, type: NotificationType, at: Instant, optedOut: Boolean) {
        if (notifications.existsByRenewalRequestIdAndNotificationType(r.id, type)) return
        notifications.save(
            RenewalNotification(r.businessId, r.id, r.customerId, type, at, deliveryStatus = if (optedOut) DeliveryStatus.SKIPPED else DeliveryStatus.PENDING),
        )
    }

    private fun skipPending(requestId: UUID) {
        notifications.findByRenewalRequestId(requestId)
            .filter { it.actionable && it.notificationType != NotificationType.FAREWELL }
            .forEach { it.deliveryStatus = DeliveryStatus.SKIPPED }
    }

    private fun find(token: String, lock: Boolean = false): RenewalRequest {
        val r = requests.findByTokenHash(SecureTokens.sha256Hex(token)) ?: throw ApiException.notFound("Este enlace de renovación no es válido.")
        if (!clock.instant().isBefore(r.tokenExpiresAt)) {
            throw ApiException(org.springframework.http.HttpStatus.GONE, "RENEWAL_LINK_EXPIRED", "Este enlace de renovación venció. Escribe al vendedor para renovar.")
        }
        return if (lock) requests.lockById(r.id)!! else r
    }

    private fun describe(r: RenewalRequest): PublicRenewalResponse {
        val business = businesses.findById(r.businessId).orElseThrow()
        val s = subscriptions.entity(r.businessId, r.subscriptionId)
        val customer = customers.findById(r.customerId).orElseThrow()
        val product = products.findById(r.productId).orElseThrow()
        val available = entitlements.canSell(business) && product.publicationStatus != PublicationStatus.ARCHIVED && product.available
        return PublicRenewalResponse(
            status = r.status,
            response = r.response,
            store = PublicRenewalStore(
                business.slug, business.businessName, business.logoUrl,
                business.primaryColor.takeIf { entitlements.hasFeature(business.id, PlanFeature.BRANDING) },
            ),
            customerFirstName = customer.fullName.trim().split(Regex("\\s+")).first(),
            productName = s.productNameSnapshot,
            durationDays = product.durationDays,
            price = product.price,
            currentExpiresAt = s.expiresAt!!,
            canRespond = r.status == RenewalStatus.OPEN,
            canPay = available && (r.status == RenewalStatus.OPEN || r.status == RenewalStatus.AWAITING_PAYMENT),
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
        val message = when (n.notificationType) {
            NotificationType.FAREWELL -> RenewalMessages.farewell(c.fullName, business.businessName)
            else -> RenewalMessages.reminder(
                c.fullName, s.productNameSnapshot, business.businessName, r.cycleExpiresAt, zone,
                dueToday = n.notificationType == NotificationType.REMINDER_DUE_DAY || r.cycleExpiresAt.atZone(zone).toLocalDate() == clock.instant().atZone(zone).toLocalDate(),
                link = link(r.id),
            )
        }
        val consent = hasReminderConsent(c.id)
        val wa = whatsAppIntegrations.active(business.id)?.integration
        // Se enviará solo por la integración oficial: el vendedor no debe enviarlo también a mano.
        val automatic = n.deliveryStatus == DeliveryStatus.PENDING && consent && wa != null &&
            (n.notificationType != NotificationType.FAREWELL || wa.farewellTemplate != null)
        return NotificationItem(
            id = n.id, type = n.notificationType,
            typeLabel = when (n.notificationType) {
                NotificationType.REMINDER_BEFORE -> "Recordatorio previo"
                NotificationType.REMINDER_DUE_DAY -> "Vence hoy"
                NotificationType.FAREWELL -> "Despedida"
            },
            customerId = c.id, customerName = c.fullName, customerPhone = c.phone, productName = s.productNameSnapshot,
            scheduledAt = n.scheduledAt, message = message, whatsappUrl = whatsapp.prepare(c.phone, message).manualUrl!!, consent = consent,
            deliveryStatus = n.deliveryStatus, automatic = automatic, failureReason = n.failureReason,
        )
    }

    /** Última decisión del cliente sobre recordatorios de renovación (historial de consentimientos). */
    fun hasReminderConsent(customerId: UUID): Boolean =
        consents.findByCustomerIdOrderByCreatedAtDesc(customerId).firstOrNull { it.purpose == ConsentPurpose.RENEWAL_REMINDERS }
            ?.let { it.granted && it.revokedAt == null } ?: false

    private companion object {
        val ACTIONABLE = setOf(DeliveryStatus.PENDING, DeliveryStatus.FAILED)
    }
}
