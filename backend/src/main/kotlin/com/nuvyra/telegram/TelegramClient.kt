package com.nuvyra.telegram

import com.nuvyra.common.config.AppProperties
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/** Bot API de Telegram. Nunca registra el token: va en la ruta de la URL y no se escribe en los logs. */
@Component
class TelegramClient(private val properties: AppProperties) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val telegram get() = properties.telegram

    private val client: RestClient = RestClient.builder()
        .baseUrl(properties.telegram.apiBaseUrl.trimEnd('/'))
        .requestFactory(
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(properties.telegram.requestTimeout)
                setReadTimeout(properties.telegram.requestTimeout)
            },
        )
        .build()

    /** Envía un aviso con un botón opcional que abre Nuvyra. Devuelve false si Telegram lo rechazó. */
    fun sendMessage(chatId: Long, text: String, buttonText: String? = null, buttonUrl: String? = null): Boolean {
        if (!telegram.enabled) return false
        val body = buildMap<String, Any> {
            put("chat_id", chatId)
            put("text", text)
            put("disable_web_page_preview", true)
            if (buttonText != null && buttonUrl != null && buttonUrl.startsWith("https://")) {
                put("reply_markup", mapOf("inline_keyboard" to listOf(listOf(mapOf("text" to buttonText, "url" to buttonUrl)))))
            }
        }
        return call("sendMessage", body)
    }

    /** Registra el webhook con el secreto que Telegram devolverá en cada llamada. */
    fun setWebhook(url: String): Boolean =
        call("setWebhook", mapOf("url" to url, "secret_token" to telegram.webhookSecret, "allowed_updates" to listOf("message")))

    private fun call(method: String, body: Map<String, Any>): Boolean = try {
        client.post().uri("/bot{token}/{method}", telegram.botToken, method)
            .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity()
        true
    } catch (ex: Exception) {
        // Solo el tipo de error: el mensaje podría incluir la URL con el token.
        log.warn("Telegram {} falló: {}", method, ex.javaClass.simpleName)
        false
    }
}
