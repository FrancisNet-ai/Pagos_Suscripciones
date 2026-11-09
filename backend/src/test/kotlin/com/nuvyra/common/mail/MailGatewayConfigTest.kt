package com.nuvyra.common.mail

import com.nuvyra.auth.MailPasswordResetNotifier
import com.nuvyra.auth.PasswordResetNotifier
import com.nuvyra.auth.PasswordResetNotifierConfig
import com.nuvyra.auth.UnconfiguredPasswordResetNotifier
import com.nuvyra.common.config.AppProperties
import com.nuvyra.staff.MailStaffInvitationNotifier
import com.nuvyra.staff.StaffInvitationNotifier
import com.nuvyra.staff.StaffInvitationNotifierConfig
import com.nuvyra.staff.UnconfiguredStaffInvitationNotifier
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner

/** Qué canal de correo queda activo según la configuración. */
class MailGatewayConfigTest {
    private fun runner(key: String) = ApplicationContextRunner()
        .withUserConfiguration(MailGatewayConfig::class.java, PasswordResetNotifierConfig::class.java, StaffInvitationNotifierConfig::class.java)
        .withBean(AppProperties::class.java, { AppProperties(mail = AppProperties.Mail(brevoApiKey = key)) })
        .withPropertyValues("app.mail.brevo-api-key=$key")

    @Test
    fun `con clave de Brevo los avisos salen por Brevo`() {
        runner("xkeysib-prueba").run { ctx ->
            assertThat(ctx.getBean(MailGateway::class.java)).isInstanceOf(BrevoMailGateway::class.java)
            assertThat(ctx.getBean(PasswordResetNotifier::class.java)).isInstanceOf(MailPasswordResetNotifier::class.java)
            assertThat(ctx.getBean(StaffInvitationNotifier::class.java)).isInstanceOf(MailStaffInvitationNotifier::class.java)
        }
    }

    @Test
    fun `sin Brevo ni SMTP no se envia correo`() {
        runner("").run { ctx ->
            assertThat(ctx.getBeanNamesForType(MailGateway::class.java)).isEmpty()
            assertThat(ctx.getBean(PasswordResetNotifier::class.java)).isInstanceOf(UnconfiguredPasswordResetNotifier::class.java)
            assertThat(ctx.getBean(StaffInvitationNotifier::class.java)).isInstanceOf(UnconfiguredStaffInvitationNotifier::class.java)
        }
    }
}
