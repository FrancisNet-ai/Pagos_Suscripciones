package com.nuvyra.orders

import com.nuvyra.common.persistence.AbstractEntity
import com.nuvyra.common.persistence.VersionedEntity
import com.nuvyra.products.ProductType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class OrderKind { PURCHASE, RENEWAL }

enum class OrderStatus { AWAITING_PAYMENT, PAYMENT_REPORTED, PAID, CANCELLED, EXPIRED }

@Entity
@Table(name = "orders")
class Order(
    @Column(nullable = false, updatable = false) val businessId: UUID,
    @Column(nullable = false, updatable = false) val orderNumber: String,
    @Column(nullable = false, updatable = false) val customerId: UUID,
    @Column(nullable = false, updatable = false) val customerNameSnapshot: String,
    @Column(nullable = false, updatable = false) val customerPhoneSnapshot: String,
    @Column(nullable = false, updatable = false) val totalAmount: BigDecimal,
    @Column(nullable = false) var publicTokenHash: String,
    @Column(updatable = false) val idempotencyKey: UUID?,
    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false) val kind: OrderKind = OrderKind.PURCHASE,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var status: OrderStatus = OrderStatus.AWAITING_PAYMENT,
    @Column(nullable = false, updatable = false) val currency: String = "PEN",
    /** Situación que el vendedor debe revisar (p. ej. pago tardío sin cupo disponible). */
    var incidentReason: String? = null,
    var cancellationReason: String? = null,
    var paidAt: Instant? = null,
    @Column(updatable = false) val renewalRequestId: UUID? = null,
) : VersionedEntity()

/** Copia histórica del producto en el momento de la compra. */
@Entity
@Table(name = "order_items")
class OrderItem(
    @Column(nullable = false, updatable = false) val orderId: UUID,
    @Column(nullable = false, updatable = false) val productId: UUID,
    @Column(nullable = false, updatable = false) val productNameSnapshot: String,
    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false) val productTypeSnapshot: ProductType,
    @Column(nullable = false, updatable = false) val unitPriceSnapshot: BigDecimal,
    @Column(updatable = false) val durationSnapshot: Int?,
    @Column(nullable = false, updatable = false) val quantity: Int = 1,
) : AbstractEntity()

interface OrderRepository : JpaRepository<Order, UUID> {
    fun findByIdAndBusinessId(id: UUID, businessId: UUID): Order?
    fun findByPublicTokenHash(hash: String): Order?
    fun findByBusinessIdAndIdempotencyKey(businessId: UUID, key: UUID): Order?

    @Query(
        """
        select o from Order o
        where o.businessId = :businessId
          and o.status in :statuses
          and o.createdAt >= :from and o.createdAt < :to
          and (:productFilter = false or exists (select 1 from OrderItem i where i.orderId = o.id and i.productId = :productId))
          and (lower(o.customerNameSnapshot) like :pattern escape '\' or o.customerPhoneSnapshot like :pattern escape '\'
               or lower(o.orderNumber) like :pattern escape '\')
        """,
    )
    fun search(
        businessId: UUID,
        statuses: Collection<OrderStatus>,
        from: Instant,
        to: Instant,
        productFilter: Boolean,
        productId: UUID,
        pattern: String,
        pageable: Pageable,
    ): Page<Order>

    fun countByBusinessIdAndStatus(businessId: UUID, status: OrderStatus): Long
    fun countByBusinessIdAndCustomerIdAndStatus(businessId: UUID, customerId: UUID, status: OrderStatus): Long

    /** Pedidos sin pago reportado más antiguos que el límite (tarea de expiración). */
    @Query("select o.id from Order o where o.status = 'AWAITING_PAYMENT' and o.createdAt < :before order by o.createdAt")
    fun findStaleAwaitingPayment(before: Instant, pageable: Pageable): List<UUID>
}

interface OrderItemRepository : JpaRepository<OrderItem, UUID> {
    fun findByOrderId(orderId: UUID): List<OrderItem>
    fun findByOrderIdIn(orderIds: Collection<UUID>): List<OrderItem>
}
