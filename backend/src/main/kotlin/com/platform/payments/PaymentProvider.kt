package com.platform.payments

import org.springframework.stereotype.Component
import java.math.BigDecimal

/** Datos que ve el comprador para pagar. El titular es el DECLARADO por el vendedor. */
data class PaymentInstructions(
    val method: PaymentMethodType,
    val methodLabel: String,
    val holderName: String,
    val phone: String,
    val qrImageUrl: String?,
    val amount: BigDecimal,
    val reference: String,
    val automaticVerification: Boolean,
)

/**
 * Proveedor de pagos de compradores. Hoy solo existe la transferencia con verificación manual.
 * Una integración automática solo podrá añadirse con una API oficial que confirme transacciones
 * reales; nunca leyendo capturas de pantalla (ver ADR-005).
 */
interface PaymentProvider {
    fun supports(method: PaymentMethodType): Boolean

    /** `true` solo si el proveedor confirma transacciones por sí mismo. */
    val automaticVerification: Boolean

    fun instructions(method: BusinessPaymentMethod, amount: BigDecimal, orderNumber: String): PaymentInstructions
}

@Component
class ManualTransferPaymentProvider : PaymentProvider {
    override fun supports(method: PaymentMethodType) = method == PaymentMethodType.YAPE || method == PaymentMethodType.PLIN

    override val automaticVerification = false

    override fun instructions(method: BusinessPaymentMethod, amount: BigDecimal, orderNumber: String) = PaymentInstructions(
        method = method.method,
        methodLabel = method.method.label,
        holderName = method.holderName,
        phone = method.phone,
        qrImageUrl = method.qrImageUrl,
        amount = amount,
        reference = orderNumber,
        automaticVerification = false,
    )
}

@Component
class PaymentProviders(private val providers: List<PaymentProvider>) {
    fun forMethod(method: PaymentMethodType): PaymentProvider =
        providers.firstOrNull { it.supports(method) } ?: error("Sin proveedor para $method")
}
