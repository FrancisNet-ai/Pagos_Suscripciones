package com.nuvyra.staff

import com.nuvyra.common.Brand
import com.nuvyra.common.mail.MailGateway
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Entrega la invitación por correo. Devuelve `true` solo si realmente se envió. */
fun interface StaffInvitationNotifier {
    fun send(email: String, fullName: String, businessName: String, link: String): Boolean
}

class MailStaffInvitationNotifier(private val mail: MailGateway) : StaffInvitationNotifier {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun send(email: String, fullName: String, businessName: String, link: String): Boolean = try {
        mail.send(
            to = email,
            toName = fullName,
            subject = "$businessName te invitó a su equipo en ${Brand.NAME}",
            text = """
                Hola, $fullName.

                $businessName te invitó a colaborar en su panel de ${Brand.NAME}. Crea tu contraseña con este enlace (vence en 7 días):
                $link

                Si no esperabas esta invitación, ignora este mensaje.

                ${Brand.NAME} · ${Brand.TAGLINE}
            """.trimIndent(),
        )
        true
    } catch (ex: Exception) {
        log.warn("No se pudo enviar la invitación por correo: {}", ex.message)
        false
    }
}

/** Sin proveedor de correo no se envía nada: el titular comparte el enlace que se le muestra. */
class UnconfiguredStaffInvitationNotifier : StaffInvitationNotifier {
    override fun send(email: String, fullName: String, businessName: String, link: String) = false
}

@Configuration(proxyBeanMethods = false)
class StaffInvitationNotifierConfig {
    @Bean
    fun staffInvitationNotifier(mail: ObjectProvider<MailGateway>): StaffInvitationNotifier =
        mail.ifAvailable?.let(::MailStaffInvitationNotifier) ?: UnconfiguredStaffInvitationNotifier()
}
