package com.nuvyra.whatsapp

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class OrderMessagesTest {
    private val data = OrderMessageData(
        orderNumber = "ORD-2026-000128",
        customerName = "Carlos Ramírez",
        customerPhoneE164 = "+51987654321",
        productName = "Servicio Premium & Familia",
        amount = BigDecimal("15"),
        methodLabel = "Yape",
        stage = OrderMessageStage.PENDING_VERIFICATION,
        statusLabel = "Pendiente de verificación",
    )

    @Test
    fun `el mensaje usa los datos reales del pedido`() {
        val text = OrderMessages.buyerToSeller(data)
        assertThat(text).startsWith("Hola, acabo de realizar un pedido en tu tienda.")
        assertThat(text).contains(
            "Pedido: ORD-2026-000128", "Cliente: Carlos Ramírez", "Celular: 987654321",
            "Producto: Servicio Premium & Familia", "Importe: S/ 15.00", "Método: Yape", "Estado: Pendiente de verificación.",
        )
        assertThat(text).endsWith("Solicito la confirmación y coordinación de mi servicio.")
    }

    @Test
    fun `adapta el texto cuando el pago ya fue verificado`() {
        val text = OrderMessages.buyerToSeller(data.copy(stage = OrderMessageStage.VERIFIED, statusLabel = "Pago verificado"))
        assertThat(text).contains("figura como verificado", "Estado: Pago verificado.")
    }

    @Test
    fun `el enlace wa_me usa el numero del vendedor y codifica el texto`() {
        val text = OrderMessages.buyerToSeller(data)
        val url = ManualWhatsAppLinkProvider.link("+51912345678", text)
        assertThat(url).startsWith("https://wa.me/51912345678?text=")
        val encoded = url.substringAfter("text=")
        assertThat(encoded).doesNotContain(" ", "+", "&", "\n").contains("%20", "%0A", "%26")
        assertThat(URLDecoder.decode(encoded, StandardCharsets.UTF_8)).isEqualTo(text)
    }

    @Test
    fun `el proveedor manual nunca marca el mensaje como enviado`() {
        val prepared = ManualWhatsAppLinkProvider().prepare("+51912345678", "Hola")
        assertThat(prepared.sent).isFalse()
        assertThat(prepared.manualUrl).isEqualTo("https://wa.me/51912345678?text=Hola")
    }
}

class ActivityTextTest {
    @Test
    fun `la actividad muestra importes con dos decimales`() {
        org.assertj.core.api.Assertions.assertThat(
            com.nuvyra.reports.ActivityText.of("PAYMENT_VERIFIED", mapOf("orderNumber" to "ORD-2026-000001", "amount" to 15.0)),
        ).isEqualTo("Pago verificado de ORD-2026-000001 · S/ 15.00")
    }
}
