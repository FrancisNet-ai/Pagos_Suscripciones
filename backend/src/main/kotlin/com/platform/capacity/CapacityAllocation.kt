package com.platform.capacity

import com.platform.common.persistence.VersionedEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

/**
 * RESERVED: retenido temporalmente mientras se paga (cuenta solo hasta `reservedUntil`).
 * OCCUPIED: acceso asignado. RELEASE_PENDING: el cliente no renueva, pero el acceso sigue vigente.
 * RELEASED / EXPIRED: el cupo vuelve a estar disponible.
 */
enum class AllocationStatus { RESERVED, OCCUPIED, RELEASE_PENDING, RELEASED, EXPIRED }

@Entity
@Table(name = "capacity_allocations")
class CapacityAllocation(
    @Column(nullable = false, updatable = false) val businessId: UUID,
    @Column(nullable = false, updatable = false) val productId: UUID,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var allocationStatus: AllocationStatus,
    var reservedUntil: Instant? = null,
    var releasedAt: Instant? = null,
) : VersionedEntity()

interface UsageRow {
    val productId: UUID
    val occupied: Long
    val reserved: Long
}

interface CapacityAllocationRepository : JpaRepository<CapacityAllocation, UUID> {
    fun findByIdAndBusinessId(id: UUID, businessId: UUID): CapacityAllocation?

    /** Uso actual por producto: ocupados (incluye liberaciones pendientes) y reservas vigentes. */
    @Query(
        """
        select a.productId as productId,
               sum(case when a.allocationStatus in ('OCCUPIED', 'RELEASE_PENDING') then 1 else 0 end) as occupied,
               sum(case when a.allocationStatus = 'RESERVED' and a.reservedUntil > :now then 1 else 0 end) as reserved
        from CapacityAllocation a
        where a.productId in :productIds
          and a.allocationStatus in ('RESERVED', 'OCCUPIED', 'RELEASE_PENDING')
        group by a.productId
        """,
    )
    fun usage(productIds: Collection<UUID>, now: Instant): List<UsageRow>
}
