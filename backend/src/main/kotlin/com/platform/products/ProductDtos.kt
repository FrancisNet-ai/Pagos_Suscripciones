package com.platform.products

import com.platform.capacity.Availability
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class ProductRequest(
    @field:NotBlank(message = "Ingresa el nombre del producto")
    @field:Size(min = 2, max = 120, message = "El nombre debe tener entre 2 y 120 caracteres")
    val name: String?,

    @field:Size(max = 2000, message = "La descripción admite hasta 2000 caracteres")
    val description: String?,

    @field:Size(max = 60, message = "La categoría admite hasta 60 caracteres")
    val category: String?,

    @field:NotNull(message = "Selecciona el tipo de producto")
    val productType: ProductType?,

    @field:NotNull(message = "Ingresa el precio")
    @field:DecimalMin(value = "0.10", message = "El precio mínimo es S/ 0.10")
    @field:DecimalMax(value = "99999.99", message = "El precio máximo es S/ 99,999.99")
    @field:Digits(integer = 5, fraction = 2, message = "Usa como máximo dos decimales")
    val price: BigDecimal?,

    @field:Min(value = 1, message = "La duración mínima es 1 día")
    @field:Max(value = 3650, message = "La duración máxima es 3650 días")
    val durationDays: Int?,

    @field:NotNull
    val capacityEnabled: Boolean?,

    @field:Min(value = 1, message = "La capacidad mínima es 1")
    @field:Max(value = 100000, message = "La capacidad máxima es 100000")
    val capacity: Int?,

    @field:NotNull
    val available: Boolean?,
)

data class ProductStatusRequest(
    @field:NotNull(message = "Indica el estado de publicación")
    val publicationStatus: PublicationStatus?,
)

data class AvailabilityDto(
    val capacityEnabled: Boolean,
    val capacity: Int?,
    val occupied: Int,
    val reserved: Int,
    val available: Int?,
    val soldOut: Boolean,
) {
    companion object {
        fun from(a: Availability) = AvailabilityDto(a.capacityEnabled, a.capacity, a.occupied, a.reserved, a.available, a.soldOut)
    }
}

data class ProductResponse(
    val id: UUID,
    val name: String,
    val description: String?,
    val category: String?,
    val productType: ProductType,
    val price: BigDecimal,
    val currency: String,
    val durationDays: Int?,
    val imageUrl: String?,
    val publicationStatus: PublicationStatus,
    val available: Boolean,
    val availability: AvailabilityDto,
    val createdAt: Instant?,
    val updatedAt: Instant?,
) {
    companion object {
        fun from(p: Product, availability: Availability) = ProductResponse(
            p.id, p.name, p.description, p.category, p.productType, p.price, "PEN", p.durationDays, p.imageUrl,
            p.publicationStatus, p.available, AvailabilityDto.from(availability), p.createdAt, p.updatedAt,
        )
    }
}

/** Filtro del listado: ACTIVE = borradores y publicados (excluye archivados). */
enum class ProductFilter(val statuses: Set<PublicationStatus>) {
    ACTIVE(setOf(PublicationStatus.DRAFT, PublicationStatus.PUBLISHED)),
    PUBLISHED(setOf(PublicationStatus.PUBLISHED)),
    DRAFT(setOf(PublicationStatus.DRAFT)),
    ARCHIVED(setOf(PublicationStatus.ARCHIVED)),
    ALL(PublicationStatus.entries.toSet()),
}
