package com.nuvyra.common.config

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
    val renewals: Renewals = Renewals(),
    val secrets: Secrets = Secrets(),
    val whatsapp: WhatsApp = WhatsApp(),
    val security: Security = Security(),
) {
    data class Secrets(
        /** Clave AES-256 en Base64 (32 bytes) para cifrar credenciales de terceros guardadas en la base. */
        val encryptionKey: String = "",
    )

    /**
     * WhatsApp Business Platform. La plataforma opera una app de Meta: el secreto firma los webhooks y el
     * token de verificación responde al alta del webhook. Cada negocio aporta su número y su token.
     */
    data class Security(
        /** El superadministrador debe activar la verificación en dos pasos para usar `/api/v1/admin`. */
        val requireSuperAdminTwoFactor: Boolean = true,
        /** Rechaza contraseñas que aparecen en filtraciones conocidas (Pwned Passwords, por prefijo de hash). */
        val breachedPasswordCheck: Boolean = true,
        val pwnedPasswordsUrl: String = "https://api.pwnedpasswords.com",
        val breachedPasswordTimeout: Duration = Duration.ofSeconds(3),
        /** Segunda barrera entre negocios en PostgreSQL (V17). Solo se apaga ante una emergencia. */
        val rowLevelSecurity: Boolean = true,
    )

    data class WhatsApp(
        val graphBaseUrl: String = "https://graph.facebook.com/v21.0",
        val appSecret: String = "",
        val verifyToken: String = "",
        val requestTimeout: Duration = Duration.ofSeconds(10),
        val maxAttempts: Int = 3,
    )

    data class Renewals(
        /**
         * Secreto para derivar los enlaces de renovación (HMAC). Permite reconstruir el enlace al
         * preparar cada aviso sin guardarlo en texto plano. Cambiarlo invalida los enlaces vigentes.
         */
        val tokenSecret: String = "",
    )

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
