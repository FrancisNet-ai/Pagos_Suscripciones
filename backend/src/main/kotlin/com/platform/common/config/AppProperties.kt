package com.platform.common.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration
import java.time.ZoneId

@ConfigurationProperties(prefix = "app")
data class AppProperties(
    /** URL pública de la SPA, usada para construir enlaces (recuperación, tiendas, renovaciones). */
    val publicBaseUrl: String = "http://localhost:4200",
    /** Zona horaria de referencia del negocio. */
    val timeZone: ZoneId = ZoneId.of("America/Lima"),
    val session: Session = Session(),
    val passwordReset: PasswordReset = PasswordReset(),
    val cors: Cors = Cors(),
    val mail: Mail = Mail(),
    val bootstrap: Bootstrap = Bootstrap(),
    val storage: Storage = Storage(),
    val checkout: Checkout = Checkout(),
) {
    data class Storage(
        /** Carpeta donde se guardan imágenes públicas (logos, portadas, productos, QR). */
        val localDir: String = "./data/media",
        /** Comprobantes de pago: nunca se sirven públicamente. */
        val privateDir: String = "./data/private",
        val maxImageBytes: Long = 2 * 1024 * 1024,
        val maxReceiptBytes: Long = 5 * 1024 * 1024,
        val maxImageDimension: Int = 4096,
    )

    data class Checkout(
        /** Tiempo que se retiene un cupo mientras el comprador paga. */
        val reservationHold: Duration = Duration.ofMinutes(30),
        /** Tiempo que se retiene el cupo tras reportar el pago, mientras el vendedor verifica. */
        val verificationHold: Duration = Duration.ofDays(2),
        /** Un pedido sin pago reportado vence tras este plazo. */
        val orderTtl: Duration = Duration.ofDays(2),
    )

    data class Session(
        val cookieName: String = "sid",
        val cookieSecure: Boolean = true,
        val idleTimeout: Duration = Duration.ofHours(12),
        val absoluteTimeout: Duration = Duration.ofDays(30),
    )

    data class PasswordReset(
        val tokenTtl: Duration = Duration.ofMinutes(30),
    )

    data class Cors(
        /** Vacío = CORS deshabilitado (SPA y API en el mismo origen). */
        val allowedOrigins: List<String> = emptyList(),
    )

    data class Mail(
        val from: String = "no-reply@localhost",
        /** Solo para desarrollo: escribir en el log los enlaces que se enviarían por correo. */
        val logLinks: Boolean = false,
    )

    data class Bootstrap(
        val superAdminEmail: String? = null,
        val superAdminPassword: String? = null,
        val superAdminName: String = "Administrador",
        val superAdminPhone: String = "+51900000000",
    )
}
