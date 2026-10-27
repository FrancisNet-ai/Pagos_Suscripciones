package com.nuvyra.whatsapp

import java.math.BigDecimal
import java.math.RoundingMode

enum class OrderMessageStage { AWAITING_PAYMENT, PENDING_VERIFICATION, VERIFIED, OTHER }

data class OrderMessageData(
    val orderNumber: String,
    val customerName: String,
    val customerPhoneE164: String,
    val productName: String,
    val amount: BigDecimal,
    val methodLabel: String,
    val stage: OrderMessageStage,
    val statusLabel: String,
)

/** Mensajes del comprador al vendedor. Solo datos reales del pedido; nunca credenciales. */
object OrderMessages {
    fun buyerToSeller(data: OrderMessageData): String {
        val opening = when (data.stage) {
            OrderMessageStage.VERIFIED -> "Hola, mi pago del pedido ${data.orderNumber} figura como verificado en tu tienda."
            else -> "Hola, acabo de realizar un pedido en tu tienda."
        }
        val closing = when (data.stage) {
            OrderMessageStage.VERIFIED -> "Quedo atento(a) a la coordinación de mi servicio."
            OrderMessageStage.AWAITING_PAYMENT -> "Te escribo para coordinar el pago y mi servicio."
            else -> "Solicito la confirmación y coordinación de mi servicio."
        }
        return buildString {
            appendLine(opening)
            appendLine("Pedido: ${data.orderNumber}")
            appendLine("Cliente: ${data.customerName}")
            appendLine("Celular: ${data.customerPhoneE164.removePrefix("+51")}")
            appendLine("Producto: ${data.productName}")
            appendLine("Importe: ${formatSoles(data.amount)}")
            appendLine("Método: ${data.methodLabel}")
            appendLine("Estado: ${data.statusLabel}.")
            append(closing)
        }
    }

    fun formatSoles(amount: BigDecimal) = "S/ " + amount.setScale(2, RoundingMode.HALF_UP).toPlainString()
}
