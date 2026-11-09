package com.nuvyra.common.mail

import com.nuvyra.common.config.AppProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.web.client.RestClient
import java.time.Duration

/**
 * Envía un correo de texto plano. Lanza una excepción si el proveedor no lo aceptó: cada llamador decide si el
 * fallo se muestra o solo se registra. Sin proveedor configurado no existe ningún bean de este tipo.
 */
fun interface MailGateway {
    fun send(to: String, toName: String, subject: String, text: String)
}

/** SMTP clásico (despliegue con Docker Compose en un servidor que permita la salida por SMTP). */
class SmtpMailGateway(private val mailSender: JavaMailSender, private val from: String) : MailGateway {
    override fun send(to: String, toName: String, subject: String, text: String) {
        mailSender.send(
            SimpleMailMessage().also {
                it.from = from
                it.setTo(to)
                it.subject = subject
                it.text = text
            },
        )
    }
}

/**
 * Brevo por su API HTTPS (`POST /v3/smtp/email`). Se usa en Render, que bloquea los puertos SMTP en el plan
 * gratuito. El remitente debe estar verificado en la cuenta de Brevo.
 */
class BrevoMailGateway(
    baseUrl: String,
    apiKey: String,
    private val fromEmail: String,
    private val fromName: String,
    timeout: Duration,
) : MailGateway {
    private val client = RestClient.builder()
        .baseUrl(baseUrl.trimEnd('/'))
        .requestFactory(SimpleClientHttpRequestFactory().apply { setConnectTimeout(timeout); setReadTimeout(timeout) })
        .defaultHeader("api-key", apiKey)
        .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
        .build()

    override fun send(to: String, toName: String, subject: String, text: String) {
        client.post().uri("/v3/smtp/email")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                mapOf(
                    "sender" to mapOf("name" to fromName, "email" to fromEmail),
                    "to" to listOf(mapOf("email" to to, "name" to toName)),
                    "subject" to subject,
                    "textContent" to text,
                ),
            )
            .retrieve()
            .toBodilessEntity()
    }
}

@Configuration(proxyBeanMethods = false)
class MailGatewayConfig {
    /** Tiene prioridad sobre SMTP: si hay clave de Brevo, se usa Brevo. */
    @Bean
    @ConditionalOnExpression("!'\${app.mail.brevo-api-key:}'.isBlank()")
    fun brevoMailGateway(properties: AppProperties): MailGateway = properties.mail.let {
        BrevoMailGateway(it.brevoBaseUrl, it.brevoApiKey, it.from, it.fromName, it.requestTimeout)
    }

    /** Un SPRING_MAIL_HOST vacío (p. ej. desde Docker Compose) equivale a no tener SMTP. */
    @Bean
    @ConditionalOnMissingBean(MailGateway::class)
    @ConditionalOnExpression("!'\${spring.mail.host:}'.isBlank()")
    fun smtpMailGateway(mailSender: JavaMailSender, properties: AppProperties): MailGateway =
        SmtpMailGateway(mailSender, properties.mail.from)
}
