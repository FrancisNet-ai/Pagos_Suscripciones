package com.platform.products

import com.platform.common.persistence.VersionedEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.LockModeType
import jakarta.persistence.Table
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.math.BigDecimal
import java.util.UUID

enum class ProductType { SUBSCRIPTION, ONE_TIME }

enum class PublicationStatus { DRAFT, PUBLISHED, ARCHIVED }

@Entity
@Table(name = "products")
class Product(
    @Column(nullable = false, updatable = false) val businessId: UUID,
    @Column(nullable = false) var name: String,
    var description: String?,
    var category: String?,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var productType: ProductType,
    @Column(nullable = false, precision = 10, scale = 2) var price: BigDecimal,
    var durationDays: Int?,
    var imageUrl: String? = null,
    @Column(nullable = false) var capacityEnabled: Boolean = false,
    var capacity: Int? = null,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var publicationStatus: PublicationStatus = PublicationStatus.DRAFT,
    @Column(name = "is_available", nullable = false) var available: Boolean = true,
) : VersionedEntity()

interface ProductRepository : JpaRepository<Product, UUID> {
    /** Toda consulta administrativa va acotada por `businessId` (ver ADR-002). */
    fun findByIdAndBusinessId(id: UUID, businessId: UUID): Product?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id and p.businessId = :businessId")
    fun lockByIdAndBusinessId(id: UUID, businessId: UUID): Product?

    @Query(
        """
        select p from Product p
        where p.businessId = :businessId
          and p.publicationStatus in :statuses
          and (lower(p.name) like :pattern escape '\' or lower(coalesce(p.category, '')) like :pattern escape '\')
        """,
    )
    fun search(businessId: UUID, statuses: Collection<PublicationStatus>, pattern: String, pageable: Pageable): Page<Product>

    fun findByBusinessIdAndPublicationStatusOrderByCreatedAtDesc(businessId: UUID, status: PublicationStatus): List<Product>

    fun countByBusinessIdAndPublicationStatus(businessId: UUID, status: PublicationStatus): Long
}
