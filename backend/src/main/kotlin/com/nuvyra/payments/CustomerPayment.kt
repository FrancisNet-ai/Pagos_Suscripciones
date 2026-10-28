package com.nuvyra.payments

import com.nuvyra.common.persistence.VersionedEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.LockModeType
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * PENDING: pedido creado, aún sin reporte. PENDING_VERIFICATION: el comprador reportó el pago.
 * VERIFIED: SOLO tras aprobación explícita del vendedor (o un proveedor autorizado en el futuro).
 * REFUNDED: el vendedor registró que devolvió el dinero. REPORTED queda reservado para integraciones.
 */
enum class PaymentStatus { PENDING, REPORTED, PENDING_VERIFICATION, VERIFIED, REJECTED, CANCELLED, REFUNDED }

/** Pago de un comprador al vendedor. No es un ingreso del SaaS. */
@Entity
@Table(name = "customer_payments")
class CustomerPayment(
    @Column(nullable = false, updatable = false) val businessId: UUID,
    @Column(nullable = false, updatable = false) val orderId: UUID,
    @Column(updatable = false) val paymentMethodId: UUID?,
    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false) val methodSnapshot: PaymentMethodType,
    @Column(nullable = false, updatable = false) val holderNameSnapshot: String,
    @Column(nullable = false, updatable = false) val receiverPhoneSnapshot: String,
    @Column(nullable = false, updatable = false) val amount: BigDecimal,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var status: PaymentStatus = PaymentStatus.PENDING,
    var transactionReference: String? = null,
    var receiptKey: String? = null,
    var reportedAt: Instant? = null,
    var verifiedAt: Instant? = null,
    var verifiedBy: UUID? = null,
    var rejectedAt: Instant? = null,
    var rejectionReason: String? = null,
    var refundedAt: Instant? = null,
    var refundedBy: UUID? = null,
    var refundReason: String? = null,
    var refundReference: String? = null,
) : VersionedEntity()

interface CustomerPaymentRepository : JpaRepository<CustomerPayment, UUID> {
    fun findFirstByOrderIdOrderByCreatedAtDesc(orderId: UUID): CustomerPayment?
    fun findByOrderIdOrderByCreatedAtDesc(orderId: UUID): List<CustomerPayment>
    fun findByOrderIdIn(orderIds: Collection<UUID>): List<CustomerPayment>
    fun findByIdAndBusinessId(id: UUID, businessId: UUID): CustomerPayment?

    /** Bloquea el pago para que dos aprobaciones simultáneas se serialicen. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from CustomerPayment p where p.id = :id and p.businessId = :businessId")
    fun lockByIdAndBusinessId(id: UUID, businessId: UUID): CustomerPayment?

    @Query(
        """
        select count(p) > 0 from CustomerPayment p
        where p.businessId = :businessId and p.methodSnapshot = :method and p.transactionReference = :reference
          and p.status in ('PENDING_VERIFICATION', 'VERIFIED') and p.id <> :excludeId
        """,
    )
    fun referenceInUse(businessId: UUID, method: PaymentMethodType, reference: String, excludeId: UUID): Boolean
}
