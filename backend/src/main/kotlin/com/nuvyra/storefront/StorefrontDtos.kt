package com.nuvyra.storefront

import com.nuvyra.payments.PaymentInstructions
import com.nuvyra.payments.PaymentMethodType
import com.nuvyra.products.ProductType
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import com.nuvyra.common.validation.PeruvianMobile
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class PublicPaymentOption(val method: PaymentMethodType, val label: String)

/** Datos públicos de la tienda: nunca incluye IDs internos, código empresarial ni datos de otros negocios. */
data class PublicStoreResponse(
    val slug: String,
    val businessName: String,
    val description: String?,
    val logoUrl: String?,
    val coverUrl: String?,
    val primaryColor: String?,
    val acceptingOrders: Boolean,
    val notice: String?,
    val paymentMethods: List<PublicPaymentOption>,
)

data class PublicProductResponse(
    val id: UUID,
    val name: String,
    val description: String?,
    val category: String?,
    val productType: ProductType,
    val price: BigDecimal,
    val currency: String,
    val durationDays: Int?,
    val imageUrl: String?,
    /** Solo se informa cuando el producto controla cupos. */
    val availableSlots: Int?,
    val soldOut: Boolean,
    val purchasable: Boolean,
)

data class CreateOrderRequest(
    @field:NotNull(message = "Selecciona un producto")
    val productId: UUID?,

    @field:NotBlank(message = "Ingresa tu nombre")
    @field:Size(min = 2, max = 120, message = "El nombre debe tener entre 2 y 120 caracteres")
    val customerName: String?,

    @field:NotBlank(message = "Ingresa tu número de celular")
    @field:PeruvianMobile
    val phone: String?,

    @field:NotNull(message = "Selecciona Yape o Plin")
    val paymentMethod: PaymentMethodType?,

    @field:AssertTrue(message = "Debes aceptar los términos de compra")
    val acceptTerms: Boolean?,

    @field:AssertTrue(message = "Debes autorizar el tratamiento de tus datos para procesar el pedido")
    val acceptDataProcessing: Boolean?,

    /** Opcional: recordatorios de renovación por WhatsApp. */
    val acceptRenewalReminders: Boolean? = false,
)

data class CreateOrderResponse(val publicToken: String, val orderNumber: String)

data class ReportPaymentFields(
    @field:NotBlank(message = "Ingresa el código de operación")
    @field:Pattern(regexp = "^[A-Za-z0-9-]{4,40}$", message = "El código de operación debe tener entre 4 y 40 letras o números")
    val transactionReference: String?,
)

data class PublicOrderResponse(
    val orderNumber: String,
    val status: String,
    val statusLabel: String,
    val createdAt: Instant?,
    val store: PublicOrderStore,
    val customerName: String,
    val customerPhoneMasked: String,
    val productName: String,
    val productType: ProductType,
    val durationDays: Int?,
    val amount: BigDecimal,
    val currency: String,
    val payment: PublicOrderPayment?,
    val reservedUntil: Instant?,
    val canReportPayment: Boolean,
    val whatsappUrl: String?,
)

data class PublicOrderStore(val slug: String, val businessName: String, val logoUrl: String?, val primaryColor: String?)

data class PublicOrderPayment(
    val status: String,
    val instructions: PaymentInstructions,
    val transactionReference: String?,
    val reportedAt: Instant?,
    val rejectionReason: String?,
)
