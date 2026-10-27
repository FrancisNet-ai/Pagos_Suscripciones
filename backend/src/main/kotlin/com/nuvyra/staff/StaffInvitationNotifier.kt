package com.nuvyra.staff

import com.nuvyra.common.Brand
import com.nuvyra.common.config.AppProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender

/** Entrega la invitación por correo. Devuelve `true` solo si realmente se envió. */
fun interface StaffInvitationNotifier {
    fun send(email: String, fullName: String, businessName: String, link: String): Boolean
}

class SmtpStaffInvitationNotifier(private val mailSender: JavaMailSender, private val properties: AppProperties) : StaffInvitationNotifier {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun send(email: String, fullName: String, businessName: String, link: String): Boolean = try {
        mailSender.send(
            SimpleMailMessage().apply {
                from = properties.mail.from
                setTo(email)
                subject = "$businessName te invitó a su equipo en ${Brand.NAME}"
                text = """
                    Hola, $fullName.

                    $businessName te invitó a colaborar en su panel de ${Brand.NAME}. Crea tu contraseña con este enlace (vence en 7 días):
                    $link

                    Si no esperabas esta invitación, ignora este mensaje.

                    ${Brand.NAME} · ${Brand.TAGLINE}
                """.trimIndent()
            },
        )
        true
    } catch (ex: Exception) {
        log.warn("No se pudo enviar la invitación por correo: {}", ex.message)
        false
    }
}

/** Sin SMTP no se envía nada: el titular comparte el enlace que se le muestra. */
class UnconfiguredStaffInvitationNotifier : StaffInvitationNotifier {
    override fun send(email: String, fullName: String, businessName: String, link: String) = false
}

@Configuration
class StaffInvitationNotifierConfig {
    @Bean
    @ConditionalOnExpression("!'\${spring.mail.host:}'.isBlank()")
    fun smtpStaffInvitationNotifier(mailSender: JavaMailSender, properties: AppProperties): StaffInvitationNotifier =
        SmtpStaffInvitationNotifier(mailSender, properties)

    @Bean
    @ConditionalOnMissingBean(StaffInvitationNotifier::class)
    fun unconfiguredStaffInvitationNotifier(): StaffInvitationNotifier = UnconfiguredStaffInvitationNotifier()
}
