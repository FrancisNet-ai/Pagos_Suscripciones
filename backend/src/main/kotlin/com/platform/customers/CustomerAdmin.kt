package com.platform.customers

import com.platform.businesses.BusinessService
import com.platform.common.audit.AuditActions
import com.platform.common.audit.AuditService
import com.platform.common.error.ApiException
import com.platform.common.validation.PeruPhone
import com.platform.common.validation.PeruvianMobile
import com.platform.common.web.PageResponse
import com.platform.orders.OrderRepository
import com.platform.orders.OrderService
import com.platform.orders.OrderStatus
import com.platform.orders.OrderSummary
import com.platform.orders.OrderFilter
import com.platform.products.ProductService
import com.platform.security.CurrentBusiness
import com.platform.subscriptions.CustomerSubscriptionRepository
import com.platform.subscriptions.ManualSubscriptionRequest
import com.platform.subscriptions.SubscriptionResponse
import com.platform.subscriptions.SubscriptionService
import com.platform.subscriptions.SubscriptionStatus
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** Filtros por situación de sus suscripciones. */
enum class CustomerFilter { ALL, ACTIVE, EXPIRING, EXPIRED }

data class CreateCustomerRequest(
    @field:NotBlank(message = "Ingresa el nombre del cliente")
    @field:Size(min = 2, max = 120, message = "El nombre debe tener entre 2 y 120 caracteres")
    val fullName: String?,

    @field:NotBlank(message = "Ingresa el celular del cliente")
    @field:PeruvianMobile
    val phone: String?,

    @field:Size(max = 1000, message = "Las notas admiten hasta 1000 caracteres")
    val notes: String?,

    /** Suscripción que el cliente ya tenía (opcional). */
    @field:Valid
    val subscription: ManualSubscriptionRequest?,
)

data class UpdateCustomerRequest(
    @field:NotBlank(message = "Ingresa el nombre del cliente")
    @field:Size(min = 2, max = 120, message = "El nombre debe tener entre 2 y 120 caracteres")
    val fullName: String?,

    @field:Size(max = 1000, message = "Las notas admiten hasta 1000 caracteres")
    val notes: String?,
)

data class CustomerSummary(
    val id: UUID,
    val fullName: String,
    val phone: String,
    val source: CustomerSource,
    val activeSubscriptions: Int,
    val nextExpiry: Instant?,
    val hasExpired: Boolean,
    val createdAt: Instant?,
)

data class CustomerDetail(
    val id: UUID,
    val fullName: String,
    val phone: String,
    val source: CustomerSource,
    val notes: String?,
    val createdAt: Instant?,
    val renewalRemindersConsent: Boolean,
    val paidOrders: Long,
    val subscriptions: List<SubscriptionResponse>,
    val orders: List<OrderSummary>,
)

interface CustomerSearchRepository : Repository<Customer, UUID> {
    /** Clientes del negocio según la situación de sus suscripciones. */
    @Query(
        """
        select c from Customer c
        where c.businessId = :businessId
          and (lower(c.fullName) like :pattern escape '\' or c.phone like :pattern escape '\')
          and (:filter = 'ALL'
               or (:filter = 'ACTIVE' and exists (select 1 from CustomerSubscription s where s.customerId = c.id
                     and s.status in (com.platform.subscriptions.SubscriptionStatus.ACTIVE, com.platform.subscriptions.SubscriptionStatus.RENEWAL_PENDING)))
               or (:filter = 'EXPIRING' and exists (select 1 from CustomerSubscription s where s.customerId = c.id
                     and s.status in (com.platform.subscriptions.SubscriptionStatus.ACTIVE, com.platform.subscriptions.SubscriptionStatus.RENEWAL_PENDING)
                     and s.expiresAt < :expiringBefore))
               or (:filter = 'EXPIRED' and exists (select 1 from CustomerSubscription s where s.customerId = c.id
                     and s.status = com.platform.subscriptions.SubscriptionStatus.EXPIRED)
                   and not exists (select 1 from CustomerSubscription s2 where s2.customerId = c.id
                     and s2.status in (com.platform.subscriptions.SubscriptionStatus.ACTIVE, com.platform.subscriptions.SubscriptionStatus.RENEWAL_PENDING))))
        """,
    )
    fun search(businessId: UUID, pattern: String, filter: String, expiringBefore: Instant, pageable: Pageable): Page<Customer>
}

@Service
class CustomerAdminService(
    private val customers: CustomerRepository,
    private val search: CustomerSearchRepository,
    private val consents: CustomerConsentRepository,
    private val subscriptionRepo: CustomerSubscriptionRepository,
    private val subscriptions: SubscriptionService,
    private val orders: OrderService,
    private val orderRepo: OrderRepository,
    private val audit: AuditService,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun list(businessId: UUID, query: String?, filter: CustomerFilter, page: Int, size: Int): PageResponse<CustomerSummary> {
        val result = search.search(
            businessId, ProductService.likePattern(query), filter.name, clock.instant().plus(Duration.ofDays(EXPIRING_DAYS)),
            PageRequest.of(maxOf(page, 0), size.coerceIn(1, PageResponse.MAX_SIZE), Sort.by("fullName")),
        )
        val subsByCustomer = if (result.isEmpty) emptyMap() else subscriptionRepo.findByCustomerIdIn(result.content.map { it.id }).groupBy { it.customerId }
        return PageResponse.of(result) { c ->
            val subs = subsByCustomer[c.id].orEmpty()
            val active = subs.filter { it.status == SubscriptionStatus.ACTIVE || it.status == SubscriptionStatus.RENEWAL_PENDING }
            CustomerSummary(
                id = c.id, fullName = c.fullName, phone = c.phone, source = c.source,
                activeSubscriptions = active.size,
                nextExpiry = active.mapNotNull { it.expiresAt }.minOrNull(),
                hasExpired = subs.any { it.status == SubscriptionStatus.EXPIRED },
                createdAt = c.createdAt,
            )
        }
    }

    @Transactional(readOnly = true)
    fun detail(businessId: UUID, zone: ZoneId, customerId: UUID): CustomerDetail {
        val c = load(businessId, customerId)
        val reminders = consents.findByCustomerIdOrderByCreatedAtDesc(c.id).firstOrNull { it.purpose == ConsentPurpose.RENEWAL_REMINDERS }
        return CustomerDetail(
            id = c.id, fullName = c.fullName, phone = c.phone, source = c.source, notes = c.notes, createdAt = c.createdAt,
            renewalRemindersConsent = reminders?.let { it.granted && it.revokedAt == null } ?: false,
            paidOrders = orderRepo.countByBusinessIdAndCustomerIdAndStatus(businessId, c.id, OrderStatus.PAID),
            subscriptions = subscriptions.forCustomer(businessId, c),
            orders = orders.list(businessId, zone, OrderFilter.ALL, null, null, null, c.phone, 0, 20).items,
        )
    }

    /** Alta manual (clientes que el vendedor ya tenía). Un celular es único dentro del negocio. */
    @Transactional
    fun create(businessId: UUID, actorId: UUID, zone: ZoneId, request: CreateCustomerRequest): CustomerDetail {
        val phone = PeruPhone.normalize(request.phone) ?: throw ApiException.invalidField("phone", "Ingresa un celular peruano válido de 9 dígitos")
        customers.findByBusinessIdAndPhone(businessId, phone)?.let { existing ->
            throw ApiException(
                HttpStatus.CONFLICT, "CUSTOMER_EXISTS", "Ya tienes un cliente con este celular: ${existing.fullName}.",
                extra = mapOf("customerId" to existing.id),
            )
        }
        val customer = customers.save(
            Customer(businessId, request.fullName!!.trim(), phone, CustomerSource.MANUAL, request.notes?.trim()?.ifEmpty { null }),
        )
        audit.record(AuditActions.CUSTOMER_CREATED, actorId = actorId, businessId = businessId, entityType = "CUSTOMER", entityId = customer.id, metadata = mapOf("name" to customer.fullName))
        request.subscription?.let { subscriptions.registerManual(businessId, actorId, customer, it, zone) }
        return detail(businessId, zone, customer.id)
    }

    @Transactional
    fun update(businessId: UUID, actorId: UUID, zone: ZoneId, customerId: UUID, request: UpdateCustomerRequest): CustomerDetail {
        val c = load(businessId, customerId)
        c.fullName = request.fullName!!.trim()
        c.notes = request.notes?.trim()?.ifEmpty { null }
        audit.record(AuditActions.CUSTOMER_UPDATED, actorId = actorId, businessId = businessId, entityType = "CUSTOMER", entityId = c.id)
        return detail(businessId, zone, c.id)
    }

    @Transactional
    fun addSubscription(businessId: UUID, actorId: UUID, zone: ZoneId, customerId: UUID, request: ManualSubscriptionRequest): CustomerDetail {
        subscriptions.registerManual(businessId, actorId, load(businessId, customerId), request, zone)
        return detail(businessId, zone, customerId)
    }

    private fun load(businessId: UUID, id: UUID) = customers.findByIdAndBusinessId(id, businessId) ?: throw ApiException.notFound("El cliente no existe.")

    companion object {
        const val EXPIRING_DAYS = 7L
    }
}

@RestController
@RequestMapping("/api/v1/customers")
@Tag(name = "Clientes")
class CustomerController(private val service: CustomerAdminService, private val businesses: BusinessService) {

    @GetMapping
    @Operation(summary = "Clientes del negocio: búsqueda por nombre o celular y filtro por situación")
    fun list(
        current: CurrentBusiness,
        @RequestParam(required = false) query: String?,
        @RequestParam(defaultValue = "ALL") filter: CustomerFilter,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ) = service.list(current.businessId, query, filter, page, size)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra un cliente existente, con su suscripción opcional (no cuenta como venta)")
    fun create(current: CurrentBusiness, @Valid @RequestBody body: CreateCustomerRequest) =
        service.create(current.businessId, current.userId, zone(current), body)

    @GetMapping("/{id}")
    fun detail(current: CurrentBusiness, @PathVariable id: UUID) = service.detail(current.businessId, zone(current), id)

    @PutMapping("/{id}")
    fun update(current: CurrentBusiness, @PathVariable id: UUID, @Valid @RequestBody body: UpdateCustomerRequest) =
        service.update(current.businessId, current.userId, zone(current), id, body)

    @PostMapping("/{id}/subscriptions")
    @Operation(summary = "Registra una suscripción antigua para un cliente existente")
    fun addSubscription(current: CurrentBusiness, @PathVariable id: UUID, @Valid @RequestBody body: ManualSubscriptionRequest) =
        service.addSubscription(current.businessId, current.userId, zone(current), id, body)

    private fun zone(current: CurrentBusiness) = ZoneId.of(businesses.get(current.businessId).timeZone)
}
