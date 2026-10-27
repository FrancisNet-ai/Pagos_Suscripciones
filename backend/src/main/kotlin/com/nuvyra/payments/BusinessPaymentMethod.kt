package com.nuvyra.payments

import com.nuvyra.common.persistence.VersionedEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

enum class PaymentMethodType(val label: String) { YAPE("Yape"), PLIN("Plin") }

/**
 * Datos de cobro que el propio vendedor declara. El titular NO está validado por el banco:
 * la interfaz debe presentarlo como "titular declarado".
 */
@Entity
@Table(name = "business_payment_methods")
class BusinessPaymentMethod(
    @Column(nullable = false, updatable = false) val businessId: UUID,
    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false) val method: PaymentMethodType,
    @Column(nullable = false) var holderName: String,
    @Column(nullable = false) var phone: String,
    var qrImageUrl: String? = null,
    @Column(name = "is_enabled", nullable = false) var enabled: Boolean = false,
) : VersionedEntity()

interface BusinessPaymentMethodRepository : JpaRepository<BusinessPaymentMethod, UUID> {
    fun findByBusinessIdOrderByMethodAsc(businessId: UUID): List<BusinessPaymentMethod>
    fun findByBusinessIdAndMethod(businessId: UUID, method: PaymentMethodType): BusinessPaymentMethod?
}
