package com.platform.whatsapp

import org.springframework.stereotype.Component
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Resultado de preparar un mensaje. Con el proveedor manual NUNCA se considera enviado:
 * solo se abre WhatsApp con el texto prellenado y la persona decide si lo envía.
 */
data class PreparedMessage(
    val text: String,
    /** Enlace `wa.me` para envío manual; `null` si el proveedor envía directamente. */
    val manualUrl: String?,
    val sent: Boolean,
    val providerMessageId: String? = null,
)

/**
 * Canal de mensajería. Implementaciones:
 * - [ManualWhatsAppLinkProvider]: disponible desde el MVP (enlaces `wa.me`).
 * - `WhatsAppCloudApiProvider`: futura, solo con credenciales, plantillas aprobadas y consentimiento.
 */
interface MessagingProvider {
    val automatic: Boolean

    /** @param toE164 número destino en formato `+51987654321`. */
    fun prepare(toE164: String, text: String): PreparedMessage
}

@Component
class ManualWhatsAppLinkProvider : MessagingProvider {
    override val automatic = false

    override fun prepare(toE164: String, text: String) = PreparedMessage(text = text, manualUrl = link(toE164, text), sent = false)

    companion object {
        fun link(toE164: String, text: String): String {
            val number = toE164.filter { it.isDigit() }
            // URLEncoder codifica espacios como '+': se usa %20 para que WhatsApp los muestre correctamente.
            val encoded = URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20")
            return "https://wa.me/$number?text=$encoded"
        }
    }
}
