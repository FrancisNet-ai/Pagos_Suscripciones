package com.platform.whatsapp

import com.platform.common.config.AppProperties
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import tools.jackson.databind.ObjectMapper

/** Error de la Graph API. `retryable`: fallo temporal (red, 429, 5xx) que puede reintentarse. */
class WhatsAppApiException(message: String, val retryable: Boolean, val code: Int? = null) : RuntimeException(message)

data class PhoneNumberInfo(val displayPhoneNumber: String?, val verifiedName: String?)

sealed interface TemplateButton {
    val index: Int

    /** Botón de respuesta rápida: el texto lo define la plantilla; el `payload` vuelve en el webhook. */
    data class QuickReply(override val index: Int, val payload: String) : TemplateButton

    /** Botón de enlace con sufijo dinámico (la plantilla define la URL base). */
    data class UrlSuffix(override val index: Int, val suffix: String) : TemplateButton
}

/**
 * Cliente mínimo de WhatsApp Business Platform (Cloud API). Solo envía plantillas aprobadas por Meta
 * (obligatorias fuera de la ventana de 24 h) y textos libres como respuesta inmediata a un cliente.
 */
@Component
class WhatsAppCloudClient(
    private val properties: AppProperties,
    private val objectMapper: ObjectMapper,
) {
    private val client: RestClient = RestClient.builder()
        .baseUrl(properties.whatsapp.graphBaseUrl.trimEnd('/'))
        .requestFactory(
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(properties.whatsapp.requestTimeout)
                setReadTimeout(properties.whatsapp.requestTimeout)
            },
        )
        .build()

    fun phoneNumber(phoneNumberId: String, token: String): PhoneNumberInfo {
        val body = call { client.get().uri("/{id}?fields=display_phone_number,verified_name", phoneNumberId).headers { it.setBearerAuth(token) }.retrieve().body(String::class.java) }.asMap()
        return PhoneNumberInfo(body?.get("display_phone_number") as String?, body?.get("verified_name") as String?)
    }

    /** Suscribe la app de la plataforma a los webhooks de la cuenta de WhatsApp Business. */
    fun subscribeApp(wabaId: String, token: String) {
        call { client.post().uri("/{id}/subscribed_apps", wabaId).headers { it.setBearerAuth(token) }.retrieve().body(String::class.java) }
    }

    fun sendTemplate(
        phoneNumberId: String,
        token: String,
        toE164: String,
        template: String,
        language: String,
        bodyParams: List<String>,
        buttons: List<TemplateButton> = emptyList(),
    ): String {
        val components = buildList {
            if (bodyParams.isNotEmpty()) add(mapOf("type" to "body", "parameters" to bodyParams.map { mapOf("type" to "text", "text" to it) }))
            buttons.forEach { b ->
                add(
                    when (b) {
                        is TemplateButton.QuickReply -> mapOf("type" to "button", "sub_type" to "quick_reply", "index" to b.index.toString(), "parameters" to listOf(mapOf("type" to "payload", "payload" to b.payload)))
                        is TemplateButton.UrlSuffix -> mapOf("type" to "button", "sub_type" to "url", "index" to b.index.toString(), "parameters" to listOf(mapOf("type" to "text", "text" to b.suffix)))
                    },
                )
            }
        }
        return send(
            phoneNumberId, token,
            mapOf(
                "messaging_product" to "whatsapp", "to" to toE164.filter(Char::isDigit), "type" to "template",
                "template" to mapOf("name" to template, "language" to mapOf("code" to language), "components" to components),
            ),
        )
    }

    /** Texto libre: solo válido dentro de las 24 h siguientes a un mensaje del cliente. */
    fun sendText(phoneNumberId: String, token: String, toE164: String, text: String): String = send(
        phoneNumberId, token,
        mapOf("messaging_product" to "whatsapp", "to" to toE164.filter(Char::isDigit), "type" to "text", "text" to mapOf("body" to text, "preview_url" to true)),
    )

    private fun send(phoneNumberId: String, token: String, payload: Map<String, Any>): String {
        val body = call {
            client.post().uri("/{id}/messages", phoneNumberId).headers { it.setBearerAuth(token) }
                .contentType(MediaType.APPLICATION_JSON).body(objectMapper.writeValueAsString(payload)).retrieve().body(String::class.java)
        }.asMap()
        val id = ((body?.get("messages") as? List<*>)?.firstOrNull() as? Map<*, *>)?.get("id") as? String
        return id ?: throw WhatsAppApiException("WhatsApp no devolvió el identificador del mensaje.", retryable = false)
    }

    private fun String?.asMap(): Map<*, *>? = this?.takeIf { it.isNotBlank() }?.let { objectMapper.readValue(it, Map::class.java) }

    private fun <T> call(block: () -> T): T = try {
        block()
    } catch (ex: RestClientResponseException) {
        val error = runCatching { objectMapper.readValue(ex.responseBodyAsString, Map::class.java)["error"] as? Map<*, *> }.getOrNull()
        val message = (error?.get("error_user_msg") ?: error?.get("message")) as? String ?: "WhatsApp respondió con el código ${ex.statusCode.value()}"
        throw WhatsAppApiException(message.take(450), retryable = ex.statusCode.is5xxServerError || ex.statusCode.value() == 429, code = (error?.get("code") as? Number)?.toInt())
    } catch (ex: ResourceAccessException) {
        throw WhatsAppApiException("No se pudo conectar con WhatsApp.", retryable = true)
    }
}
