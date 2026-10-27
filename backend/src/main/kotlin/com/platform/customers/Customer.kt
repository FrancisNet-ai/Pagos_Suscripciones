package com.platform.customers

import com.platform.common.persistence.AbstractEntity
import com.platform.common.persistence.VersionedEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

enum class CustomerSource { STOREFRONT, MANUAL, IMPORT }

/** Comprador de un negocio. No tiene cuenta ni contraseña; solo existe dentro de su negocio. */
@Entity
@Table(name = "customers")
class Customer(
    @Column(nullable = false, updatable = false) val businessId: UUID,
    @Column(nullable = false) var fullName: String,
    @Column(nullable = false) var phone: String,
    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false) val source: CustomerSource,
    var notes: String? = null,
) : VersionedEntity()

interface CustomerRepository : JpaRepository<Customer, UUID> {
    fun findByBusinessIdAndPhone(businessId: UUID, phone: String): Customer?
    fun findByIdAndBusinessId(id: UUID, businessId: UUID): Customer?
}

enum class ConsentPurpose { PURCHASE_TERMS, DATA_PROCESSING, RENEWAL_REMINDERS, MARKETING_WHATSAPP }

/** Historial de autorizaciones: nunca se sobrescribe, cada decisión es una fila nueva. */
@Entity
@Table(name = "customer_consents")
class CustomerConsent(
    @Column(nullable = false, updatable = false) val businessId: UUID,
    @Column(nullable = false, updatable = false) val customerId: UUID,
    @Column(updatable = false) val orderId: UUID?,
    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false) val purpose: ConsentPurpose,
    @Column(nullable = false, updatable = false) val granted: Boolean,
    @Column(nullable = false, updatable = false) val source: String,
    @Column(nullable = false, updatable = false) val policyVersion: String,
    @Column(nullable = false, updatable = false) val createdAt: Instant,
    var revokedAt: Instant? = null,
) : AbstractEntity()

interface CustomerConsentRepository : JpaRepository<CustomerConsent, UUID> {
    fun findByCustomerIdOrderByCreatedAtDesc(customerId: UUID): List<CustomerConsent>
}
