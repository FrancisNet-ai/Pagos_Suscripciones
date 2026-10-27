package com.platform.capacity

import com.platform.common.error.ApiException
import com.platform.products.Product
import com.platform.products.ProductRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.util.UUID

data class Availability(
    val capacityEnabled: Boolean,
    val capacity: Int?,
    val occupied: Int,
    val reserved: Int,
    /** `null` cuando el producto no controla cupos (no se muestran cantidades artificiales). */
    val available: Int?,
) {
    val soldOut: Boolean get() = capacityEnabled && (available ?: 0) <= 0
}

/**
 * Control de cupos en el servidor. Toda operación que pueda ocupar un cupo bloquea primero la
 * fila del producto (`SELECT … FOR UPDATE`), de modo que dos compras simultáneas del último cupo
 * se serializan y solo una lo obtiene.
 */
@Service
class CapacityService(
    private val allocations: CapacityAllocationRepository,
    private val products: ProductRepository,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun availability(product: Product): Availability = availabilityFor(listOf(product)).getValue(product.id)

    @Transactional(readOnly = true)
    fun availabilityFor(products: Collection<Product>): Map<UUID, Availability> {
        val withCapacity = products.filter { it.capacityEnabled }.map { it.id }
        val usage = if (withCapacity.isEmpty()) emptyMap() else allocations.usage(withCapacity, clock.instant()).associateBy { it.productId }
        return products.associate { product ->
            val row = usage[product.id]
            val occupied = row?.occupied?.toInt() ?: 0
            val reserved = row?.reserved?.toInt() ?: 0
            product.id to Availability(
                capacityEnabled = product.capacityEnabled,
                capacity = product.capacity,
                occupied = occupied,
                reserved = reserved,
                available = product.capacity?.takeIf { product.capacityEnabled }?.let { maxOf(it - occupied - reserved, 0) },
            )
        }
    }

    /**
     * Reserva temporalmente un cupo. Devuelve `null` si el producto no controla cupos.
     * Debe llamarse dentro de la transacción que crea el pedido.
     */
    @Transactional
    fun reserve(businessId: UUID, productId: UUID, holdFor: Duration): CapacityAllocation? {
        val product = products.lockByIdAndBusinessId(productId, businessId) ?: throw ApiException.notFound("El producto no existe.")
        if (!product.capacityEnabled) return null
        if (availability(product).soldOut) throw soldOut()
        return allocations.save(
            CapacityAllocation(
                businessId = businessId,
                productId = productId,
                allocationStatus = AllocationStatus.RESERVED,
                reservedUntil = clock.instant().plus(holdFor),
            ),
        )
    }

    /** Convierte una reserva en cupo ocupado (pago verificado). Una reserva vencida solo se confirma si aún hay espacio. */
    @Transactional
    fun occupy(businessId: UUID, allocationId: UUID): CapacityAllocation {
        val allocation = load(businessId, allocationId)
        when (allocation.allocationStatus) {
            AllocationStatus.OCCUPIED -> return allocation
            AllocationStatus.RESERVED, AllocationStatus.EXPIRED -> {
                val stillHeld = allocation.allocationStatus == AllocationStatus.RESERVED && allocation.reservedUntil!!.isAfter(clock.instant())
                if (!stillHeld) {
                    val product = products.lockByIdAndBusinessId(allocation.productId, businessId)!!
                    if (availability(product).soldOut) throw soldOut()
                }
                allocation.allocationStatus = AllocationStatus.OCCUPIED
                allocation.reservedUntil = null
            }
            else -> throw ApiException.conflict("ALLOCATION_NOT_ACTIVE", "La asignación de cupo ya fue liberada.")
        }
        return allocation
    }

    /** Libera el cupo cuando el acceso realmente terminó. Idempotente. */
    @Transactional
    fun release(businessId: UUID, allocationId: UUID): CapacityAllocation {
        val allocation = load(businessId, allocationId)
        if (allocation.allocationStatus != AllocationStatus.RELEASED) {
            allocation.allocationStatus = AllocationStatus.RELEASED
            allocation.reservedUntil = null
            allocation.releasedAt = clock.instant()
        }
        return allocation
    }

    private fun load(businessId: UUID, allocationId: UUID) =
        allocations.findByIdAndBusinessId(allocationId, businessId) ?: throw ApiException.notFound()

    private fun soldOut() = ApiException(HttpStatus.CONFLICT, "PRODUCT_SOLD_OUT", "Agotado — Actualmente no hay cupos disponibles.")
}
