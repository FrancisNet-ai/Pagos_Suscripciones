package com.platform.renewals

import com.platform.payments.PaymentMethodType
import com.platform.subscriptions.SubscriptionStatus
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class RenewalResponseRequest(
    @field:NotNull(message = "Elige si deseas renovar")
    val response: RenewalResponse?,
)

data class RenewalOrderRequest(
    @field:NotNull(message = "Selecciona Yape o Plin")
    val paymentMethod: PaymentMethodType?,
)

data class PublicRenewalResponse(
    val status: RenewalStatus,
    val response: RenewalResponse,
    val store: PublicRenewalStore,
    val customerFirstName: String,
    val productName: String,
    val durationDays: Int?,
    /** Precio vigente del producto para el nuevo periodo. */
    val price: BigDecimal,
    val currentExpiresAt: Instant,
    val canRespond: Boolean,
    val canPay: Boolean,
    val paymentMethods: List<PublicMethod>,
    val farewellMessage: String?,
    val optedOut: Boolean,
)

data class PublicRenewalStore(val slug: String, val businessName: String, val logoUrl: String?, val primaryColor: String?)

data class PublicMethod(val method: PaymentMethodType, val label: String)

data class RenewalSummary(
    val id: UUID,
    val customerId: UUID,
    val customerName: String,
    val customerPhone: String,
    val subscriptionId: UUID,
    val subscriptionStatus: SubscriptionStatus,
    val productName: String,
    val cycleExpiresAt: Instant,
    val status: RenewalStatus,
    val response: RenewalResponse,
    val respondedAt: Instant?,
    val orderId: UUID?,
    val renewalUrl: String,
)

data class NotificationItem(
    val id: UUID,
    val type: NotificationType,
    val typeLabel: String,
    val customerId: UUID,
    val customerName: String,
    val customerPhone: String,
    val productName: String,
    val scheduledAt: Instant,
    val message: String,
    /** Enlace `wa.me`: abre WhatsApp con el texto; el vendedor decide enviarlo. */
    val whatsappUrl: String?,
    /** El cliente autorizó recordatorios al comprar. */
    val consent: Boolean,
)

data class RenewalsOverview(
    val notificationsDue: Long,
    val upcoming: Long,
    val accepted: Long,
    val declined: Long,
    val noResponse: Long,
    val completed: Long,
    val capacityReleasePending: Long,
)

data class RenewalSettingsRequest(
    @field:NotNull @field:Min(value = 0, message = "Mínimo 0 días") @field:Max(value = 15, message = "Máximo 15 días")
    val reminderDaysBefore: Int?,
    @field:NotNull
    val reminderOnDueDay: Boolean?,
    @field:NotNull @field:Min(value = 0, message = "Mínimo 0 días") @field:Max(value = 30, message = "Máximo 30 días")
    val renewalGraceDays: Int?,
)

data class RenewalSettingsResponse(val reminderDaysBefore: Int, val reminderOnDueDay: Boolean, val renewalGraceDays: Int)

data class RenewalHistoryItem(
    val id: UUID,
    val orderId: UUID,
    val previousExpiresAt: Instant,
    val newExpiresAt: Instant,
    val amount: BigDecimal,
    val confirmedAt: Instant,
)
