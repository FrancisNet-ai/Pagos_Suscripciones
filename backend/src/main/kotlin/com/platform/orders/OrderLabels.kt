package com.platform.orders

import com.platform.payments.PaymentStatus
import com.platform.whatsapp.OrderMessageStage

/** Estado real del pedido en lenguaje del comprador. Nunca adelanta una verificación. */
object OrderLabels {
    fun status(order: OrderStatus, payment: PaymentStatus?): String = when (order) {
        OrderStatus.AWAITING_PAYMENT -> if (payment == PaymentStatus.REJECTED) "Pago rechazado" else "Pendiente de pago"
        OrderStatus.PAYMENT_REPORTED -> "Pendiente de verificación"
        OrderStatus.PAID -> "Pago verificado"
        OrderStatus.CANCELLED -> "Pedido cancelado"
        OrderStatus.EXPIRED -> "Pedido vencido"
    }

    fun messageStage(order: OrderStatus) = when (order) {
        OrderStatus.AWAITING_PAYMENT -> OrderMessageStage.AWAITING_PAYMENT
        OrderStatus.PAYMENT_REPORTED -> OrderMessageStage.PENDING_VERIFICATION
        OrderStatus.PAID -> OrderMessageStage.VERIFIED
        else -> OrderMessageStage.OTHER
    }

    /** `+51987654321` → `*** *** 321` */
    fun maskPhone(e164: String) = "*** *** " + e164.takeLast(3)
}
