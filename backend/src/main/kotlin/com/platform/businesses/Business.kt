package com.platform.businesses

import com.platform.common.persistence.VersionedEntity
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
) : VersionedEntity()

interface BusinessRepository : JpaRepository<Business, UUID> {
    fun existsBySlug(slug: String): Boolean
    fun existsByUniqueCode(uniqueCode: String): Boolean
    fun findBySlug(slug: String): Business?
}
