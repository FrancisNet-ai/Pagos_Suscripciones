package com.platform.saas

import com.platform.common.validation.PeruvianMobile
import com.platform.payments.PaymentMethodType
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class PlanRequest(
    @field:NotBlank(message = "Ingresa el nombre del plan")
    @field:Size(min = 2, max = 80, message = "El nombre debe tener entre 2 y 80 caracteres")
    val name: String?,
    @field:Size(max = 500, message = "La descripción admite hasta 500 caracteres")
    val description: String?,
    @field:NotNull(message = "Ingresa el precio")
    @field:DecimalMin(value = "0.00", message = "El precio no puede ser negativo")
    @field:DecimalMax(value = "99999.99", message = "El precio es demasiado alto")
    @field:Digits(integer = 5, fraction = 2, message = "Usa hasta 2 decimales")
    val price: BigDecimal?,
    @field:NotNull(message = "Selecciona el periodo de cobro")
    val billingPeriod: BillingPeriod?,
    @field:Min(value = 1, message = "El límite debe ser mayor que cero") @field:Max(value = 100000)
    val productLimit: Int?,
    @field:Min(value = 1, message = "El límite debe ser mayor que cero") @field:Max(value = 1000000)
    val customerLimit: Int?,
    @field:NotNull @field:Min(value = 1, message = "Debe permitir al menos un administrador") @field:Max(value = 100)
    val adminUserLimit: Int?,
    @field:NotNull val featureReports: Boolean?,
    @field:NotNull val featureReminders: Boolean?,
    @field:NotNull val featureBranding: Boolean?,
    @field:NotNull val active: Boolean?,
    @field:NotNull @field:Min(0) @field:Max(1000) val sortOrder: Int?,
)

data class PlanResponse(
    val id: UUID,
    val name: String,
    val description: String?,
    val price: BigDecimal,
    val billingPeriod: BillingPeriod,
    val billingPeriodLabel: String,
    val productLimit: Int?,
    val customerLimit: Int?,
    val adminUserLimit: Int,
    val featureReports: Boolean,
    val featureReminders: Boolean,
    val featureBranding: Boolean,
    val active: Boolean,
    val sortOrder: Int,
    /** Negocios con este plan vigente o en gracia (solo para el superadministrador). */
    val subscribers: Long? = null,
) {
    companion object {
        fun of(p: SaasPlan, subscribers: Long? = null) = PlanResponse(
            p.id, p.name, p.description, p.price, p.billingPeriod, p.billingPeriod.label, p.productLimit, p.customerLimit,
            p.adminUserLimit, p.featureReports, p.featureReminders, p.featureBranding, p.active, p.sortOrder, subscribers,
        )
    }
}

data class CheckoutRequest(@field:NotNull(message = "Selecciona un plan") val planId: UUID?)

/** Resumen de acceso que acompaña a la sesión del vendedor. */
data class AccessSummary(
    val active: Boolean,
    val block: AccessBlock?,
    val message: String?,
    val subscriptionStatus: SaasSubscriptionStatus?,
    val planName: String?,
    val expiresAt: Instant?,
    val usableUntil: Instant?,
    val features: Set<PlanFeature>,
) {
    companion object {
        fun of(a: BusinessAccess) = AccessSummary(
            active = a.active,
            block = a.block,
            message = a.block?.message,
            subscriptionStatus = a.subscriptionStatus,
            planName = a.plan?.name,
            expiresAt = a.expiresAt,
            usableUntil = a.usableUntil,
            features = if (a.active) a.plan?.features.orEmpty() else emptySet(),
        )
    }
}

data class UsageLine(val used: Long, val limit: Int?)

data class PlatformPaymentOption(
    val method: PaymentMethodType,
    val methodLabel: String,
    /** Titular declarado por la plataforma. */
    val holderName: String,
    val phone: String,
    val qrImageUrl: String?,
)

data class SupportContact(val whatsapp: String?, val whatsappUrl: String?, val email: String?)

data class SaasPaymentSummary(
    val id: UUID,
    val method: PaymentMethodType,
    val amount: BigDecimal,
    val transactionReference: String,
    val hasReceipt: Boolean,
    val status: SaasPaymentStatus,
    val reportedAt: Instant,
    val verifiedAt: Instant?,
    val rejectionReason: String?,
)

data class InvoiceResponse(
    val id: UUID,
    val invoiceNumber: String,
    val planId: UUID,
    val planName: String,
    val billingPeriod: BillingPeriod,
    val billingPeriodLabel: String,
    val kind: InvoiceKind,
    val kindLabel: String,
    val amount: BigDecimal,
    val status: InvoiceStatus,
    val periodStart: Instant?,
    val periodEnd: Instant?,
    val paidAt: Instant?,
    val createdAt: Instant,
    val payments: List<SaasPaymentSummary>,
)

data class BillingOverview(
    val access: AccessSummary,
    val suspensionReason: String?,
    val currentPlan: PlanResponse?,
    val startAt: Instant?,
    val graceDays: Int,
    val products: UsageLine,
    val customers: UsageLine,
    val openInvoice: InvoiceResponse?,
    val paymentOptions: List<PlatformPaymentOption>,
    val support: SupportContact,
    val plans: List<PlanResponse>,
    val history: List<InvoiceResponse>,
)

/** Campos del reporte de pago SaaS (multipart junto con el comprobante opcional). */
data class SaasPaymentFields(
    @field:NotNull(message = "Selecciona Yape o Plin")
    val method: PaymentMethodType?,
    @field:NotBlank(message = "Ingresa el código de operación")
    @field:Size(max = 40, message = "El código de operación es demasiado largo")
    val transactionReference: String?,
)

data class PlatformSettingsRequest(
    @field:PeruvianMobile
    val supportWhatsapp: String?,
    @field:Email(message = "Ingresa un correo válido") @field:Size(max = 254)
    val supportEmail: String?,
    @field:NotNull @field:Min(value = 0, message = "Mínimo 0 días") @field:Max(value = 30, message = "Máximo 30 días")
    val saasGraceDays: Int?,
)

data class PlatformPaymentMethodRequest(
    @field:Size(max = 120, message = "El nombre admite hasta 120 caracteres")
    val holderName: String?,
    @field:PeruvianMobile
    val phone: String?,
    @field:NotNull val enabled: Boolean?,
)

data class PlatformPaymentMethodResponse(
    val method: PaymentMethodType,
    val methodLabel: String,
    val holderName: String?,
    val phone: String?,
    val qrImageUrl: String?,
    val enabled: Boolean,
)

data class PlatformSettingsResponse(
    val supportWhatsapp: String?,
    val supportEmail: String?,
    val saasGraceDays: Int,
    val paymentMethods: List<PlatformPaymentMethodResponse>,
)
