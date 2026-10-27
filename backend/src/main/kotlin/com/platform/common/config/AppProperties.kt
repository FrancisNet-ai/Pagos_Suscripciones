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
) {
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
