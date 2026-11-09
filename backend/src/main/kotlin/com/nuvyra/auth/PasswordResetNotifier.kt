package com.nuvyra.auth

import com.nuvyra.common.Brand
import com.nuvyra.common.config.AppProperties
import com.nuvyra.common.mail.MailGateway
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Canal para entregar el enlace de recuperación. */
fun interface PasswordResetNotifier {
    fun send(email: String, fullName: String, resetLink: String)
}

/** Envío real por el proveedor configurado (Brevo o SMTP). */
class MailPasswordResetNotifier(private val mail: MailGateway) : PasswordResetNotifier {
    override fun send(email: String, fullName: String, resetLink: String) {
        mail.send(
            to = email,
            toName = fullName,
            subject = "Restablece tu contraseña de ${Brand.NAME}",
            text = """
                Hola, $fullName.

                Recibimos una solicitud para restablecer tu contraseña de ${Brand.NAME}. Usa este enlace dentro de los próximos minutos:
                $resetLink

                Si no lo solicitaste, ignora este mensaje: tu contraseña seguirá siendo la misma.

                ${Brand.NAME} · ${Brand.TAGLINE}
            """.trimIndent(),
        )
    }
}

/**
 * Sin proveedor de correo no se envía nada. En desarrollo (`app.mail.log-links=true`)
 * el enlace se escribe en el log para poder probar el flujo; en otros entornos solo se advierte.
 */
class UnconfiguredPasswordResetNotifier(private val properties: AppProperties) : PasswordResetNotifier {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun send(email: String, fullName: String, resetLink: String) {
        if (properties.mail.logLinks) {
            log.info("[DESARROLLO] Enlace de recuperación (no se envió correo): {}", resetLink)
        } else {
            log.warn("Correo no configurado: no se pudo enviar el correo de recuperación de contraseña")
        }
    }
}

@Configuration(proxyBeanMethods = false)
class PasswordResetNotifierConfig {
    @Bean
    fun passwordResetNotifier(mail: ObjectProvider<MailGateway>, properties: AppProperties): PasswordResetNotifier =
        mail.ifAvailable?.let(::MailPasswordResetNotifier) ?: UnconfiguredPasswordResetNotifier(properties)
}
