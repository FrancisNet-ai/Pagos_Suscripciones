package com.nuvyra.businesses

import com.nuvyra.common.persistence.VersionedEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

enum class BusinessStatus { ACTIVE, SUSPENDED }

@Entity
@Table(name = "businesses")
class Business(
    @Column(nullable = false, updatable = false) val ownerId: UUID,
    @Column(nullable = false, updatable = false) val uniqueCode: String,
    @Column(nullable = false) var slug: String,
    @Column(nullable = false) var businessName: String,
    var description: String? = null,
    var logoUrl: String? = null,
    var coverUrl: String? = null,
    var primaryColor: String? = null,
    var whatsappNumber: String? = null,
    @Column(nullable = false) var timeZone: String = "America/Lima",
    @Enumerated(EnumType.STRING) @Column(nullable = false) var status: BusinessStatus = BusinessStatus.ACTIVE,
    /** Días antes del vencimiento para el primer recordatorio (0 = sin recordatorio previo). */
    @Column(nullable = false) var reminderDaysBefore: Int = 3,
    @Column(nullable = false) var reminderOnDueDay: Boolean = true,
    /** Días tras el vencimiento en que se mantiene el cupo esperando la renovación. */
    @Column(nullable = false) var renewalGraceDays: Int = 3,
    /** Motivo de la suspensión administrativa (SUPER_ADMIN); visible para el vendedor. */
    var suspensionReason: String? = null,
    var suspendedAt: java.time.Instant? = null,
) : VersionedEntity()

interface BusinessRepository : JpaRepository<Business, UUID> {
    fun existsBySlug(slug: String): Boolean
    fun existsByUniqueCode(uniqueCode: String): Boolean
    fun findBySlug(slug: String): Business?
}
