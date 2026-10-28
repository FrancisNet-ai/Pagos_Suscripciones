package com.nuvyra.common.config

import org.springframework.boot.context.event.ApplicationStartedEvent
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import java.net.URI

/**
 * En producción, la aplicación no arranca con claves de desarrollo, con valores de ejemplo ni con
 * cookies sin `Secure`. Fallar al iniciar evita desplegar por descuido con secretos conocidos.
 */
@Component
@Profile("prod")
class ProductionSafetyCheck(private val properties: AppProperties, private val environment: Environment) {

    @EventListener(ApplicationStartedEvent::class)
    fun verify() {
        val problems = problems(properties, environment.getProperty("spring.datasource.password"))
        check(problems.isEmpty()) {
            "Configuración insegura para producción:\n - " + problems.joinToString("\n - ")
        }
    }

    companion object {
        /** Valores publicados en el repositorio (perfil dev y .env.example): nunca válidos en producción. */
        private val KNOWN_DEV_VALUES = setOf(
            "dev-only-renewal-secret-change-me-0123456789",
            "ZGV2LW9ubHktZW5jcnlwdGlvbi1rZXktMzJieXRlcyE=",
        )
        private val PLACEHOLDER = Regex("(?i)(change[-_ ]?me|dev[-_]only|reemplaza|cambia|ejemplo|example|placeholder)")

        /** Prueba local con Docker (`http://localhost`): se admiten HTTP y cookies sin `Secure`, nunca secretos de ejemplo. */
        private fun isLocal(url: String) = runCatching { URI(url).host }.getOrNull() in setOf("localhost", "127.0.0.1")

        fun problems(p: AppProperties, databasePassword: String? = null): List<String> = buildList {
            fun secret(name: String, value: String?, required: Boolean) {
                val v = value?.trim().orEmpty()
                when {
                    v.isEmpty() -> if (required) add("$name no está definido")
                    v in KNOWN_DEV_VALUES || PLACEHOLDER.containsMatchIn(v) -> add("$name usa un valor de desarrollo o de ejemplo")
                }
            }
            secret("APP_RENEWAL_TOKEN_SECRET", p.renewals.tokenSecret, required = true)
            // Opcional: sin clave no se guardan tokens de WhatsApp, pero si existe no puede ser la pública.
            secret("APP_SECRETS_ENCRYPTION_KEY", p.secrets.encryptionKey, required = false)
            secret("APP_WHATSAPP_APP_SECRET", p.whatsapp.appSecret, required = false)
            secret("APP_WHATSAPP_VERIFY_TOKEN", p.whatsapp.verifyToken, required = false)
            p.bootstrap.superAdminPassword?.takeIf { it.isNotBlank() }?.let { pass ->
                if (pass.length < 12 || PLACEHOLDER.containsMatchIn(pass)) add("APP_BOOTSTRAP_SUPER_ADMIN_PASSWORD es débil o de ejemplo (mínimo 12 caracteres)")
            }
            databasePassword?.let { if (it == "nuvyra" || PLACEHOLDER.containsMatchIn(it)) add("DB_PASSWORD usa un valor por defecto o de ejemplo") }
            if (!isLocal(p.publicBaseUrl)) {
                if (!p.session.cookieSecure) add("APP_SESSION_COOKIE_SECURE debe ser true (las cookies de sesión solo viajan por HTTPS)")
                if (!p.publicBaseUrl.startsWith("https://")) add("APP_PUBLIC_BASE_URL debe usar https://")
            }
        }
    }
}
