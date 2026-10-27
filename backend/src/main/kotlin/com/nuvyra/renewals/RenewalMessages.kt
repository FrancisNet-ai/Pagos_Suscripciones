package com.nuvyra.renewals

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Textos de renovación. Usan solo datos reales; el enlace no contiene datos personales. */
object RenewalMessages {
    private val DATE = DateTimeFormatter.ofPattern("d 'de' MMMM", Locale.forLanguageTag("es-PE"))

    fun reminder(customerName: String, productName: String, businessName: String, expiresAt: Instant, zone: ZoneId, dueToday: Boolean, link: String): String {
        val first = customerName.trim().split(Regex("\\s+")).first()
        val whenText = if (dueToday) "vence hoy" else "vence el ${DATE.format(expiresAt.atZone(zone))}"
        return """
            Hola, $first.
            Te informamos que tu suscripción a $productName de $businessName $whenText.
            ¿Deseas renovar tu servicio? Elige una opción aquí:
            $link
        """.trimIndent()
    }

    fun farewell(customerName: String, businessName: String): String {
        val first = customerName.trim().split(Regex("\\s+")).first()
        return "Gracias por haber confiado en $businessName, $first.\n" +
            "Lamentamos que por ahora no continúes con nosotros. Agradecemos tu preferencia y estaremos disponibles si deseas volver."
    }
}
