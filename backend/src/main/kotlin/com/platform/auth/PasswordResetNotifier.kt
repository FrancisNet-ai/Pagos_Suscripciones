package com.platform.auth

import com.platform.common.config.AppProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender

/** Canal para entregar el enlace de recuperación. */
fun interface PasswordResetNotifier {
    fun send(email: String, fullName: String, resetLink: String)
}

/** Envío real por SMTP; se activa cuando `spring.mail.host` tiene valor. */
class SmtpPasswordResetNotifier(
    private val mailSender: JavaMailSender,
    private val properties: AppProperties,
) : PasswordResetNotifier {
    override fun send(email: String, fullName: String, resetLink: String) {
        val message = SimpleMailMessage().apply {
            from = properties.mail.from
            setTo(email)
            subject = "Restablece tu contraseña"
            text = """
                Hola, $fullName.

                Recibimos una solicitud para restablecer tu contraseña. Usa este enlace dentro de los próximos minutos:
                $resetLink

                Si no lo solicitaste, ignora este mensaje: tu contraseña seguirá siendo la misma.
            """.trimIndent()
        }
        mailSender.send(message)
    }
}

/**
 * Sin SMTP configurado no se envía ningún correo. En desarrollo (`app.mail.log-links=true`)
 * el enlace se escribe en el log para poder probar el flujo; en otros entornos solo se advierte.
 */
class UnconfiguredPasswordResetNotifier(private val properties: AppProperties) : PasswordResetNotifier {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun send(email: String, fullName: String, resetLink: String) {
        if (properties.mail.logLinks) {
            log.info("[DESARROLLO] Enlace de recuperación (no se envió correo): {}", resetLink)
        } else {
            log.warn("SMTP no configurado: no se pudo enviar el correo de recuperación de contraseña")
        }
    }
}

@Configuration
class PasswordResetNotifierConfig {
    @Bean
    // Un SPRING_MAIL_HOST vacío (p. ej. desde Docker Compose) equivale a no tener SMTP.
    @ConditionalOnExpression("!'\${spring.mail.host:}'.isBlank()")
    fun smtpPasswordResetNotifier(mailSender: JavaMailSender, properties: AppProperties): PasswordResetNotifier =
        SmtpPasswordResetNotifier(mailSender, properties)

    @Bean
    @ConditionalOnMissingBean(PasswordResetNotifier::class)
    fun unconfiguredPasswordResetNotifier(properties: AppProperties): PasswordResetNotifier =
        UnconfiguredPasswordResetNotifier(properties)
}
