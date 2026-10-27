package com.nuvyra.subscriptions

import com.nuvyra.capacity.AllocationStatus
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Estado con el que se registra una suscripción antigua. */
enum class ManualStatus { ACTIVE, EXPIRED, CANCELLED }

data class ManualSubscriptionRequest(
    @field:NotNull(message = "Selecciona el producto contratado")
    val productId: UUID?,

    @field:NotNull(message = "Indica la fecha de inicio")
    val startDate: LocalDate?,

    @field:NotNull(message = "Indica la duración")
    @field:Min(value = 1, message = "La duración mínima es 1 día")
    @field:Max(value = 3650, message = "La duración máxima es 3650 días")
    val durationDays: Int?,

    @field:NotNull(message = "Indica el importe pagado")
    @field:DecimalMin(value = "0.00", message = "El importe no puede ser negativo")
    @field:DecimalMax(value = "99999.99", message = "El importe es demasiado alto")
    @field:Digits(integer = 5, fraction = 2, message = "Usa como máximo dos decimales")
    val contractedPrice: BigDecimal?,

    @field:NotNull(message = "Indica el estado actual")
    val status: ManualStatus?,

    @field:Size(max = 1000, message = "Las notas admiten hasta 1000 caracteres")
    val notes: String?,
)

data class ActivateRequest(
    /** Inicio real del servicio; por defecto, ahora. */
    val startAt: Instant?,
)

data class SubscriptionResponse(
    val id: UUID,
    val customerId: UUID,
    val customerName: String,
    val customerPhone: String,
    val productId: UUID,
    val productName: String,
    val orderId: UUID?,
    val origin: SubscriptionOrigin,
    val status: SubscriptionStatus,
    val statusLabel: String,
    val durationDays: Int,
    val contractedPrice: BigDecimal,
    val paidAt: Instant?,
    val startAt: Instant?,
    val expiresAt: Instant?,
    val daysRemaining: Long?,
    val capacityStatus: AllocationStatus?,
    val cancellationReason: String?,
    val notes: String?,
    val createdAt: Instant?,
)

object SubscriptionLabels {
    fun of(status: SubscriptionStatus) = when (status) {
        SubscriptionStatus.PENDING_ACTIVATION -> "Por activar"
        SubscriptionStatus.ACTIVE -> "Activa"
        SubscriptionStatus.RENEWAL_PENDING -> "Renovación pendiente"
        SubscriptionStatus.EXPIRED -> "Vencida"
        SubscriptionStatus.CANCELLED -> "Cancelada"
        SubscriptionStatus.ENDED -> "Finalizada"
    }
}
