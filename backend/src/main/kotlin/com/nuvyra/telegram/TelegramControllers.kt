package com.nuvyra.telegram

import tools.jackson.databind.ObjectMapper
import com.nuvyra.common.config.AppProperties
import com.nuvyra.security.AuthenticatedUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.security.MessageDigest

/** Conexión de la cuenta (vendedor, colaborador o superadministrador) con el bot de avisos. */
@RestController
@RequestMapping("/api/v1/telegram")
@Tag(name = "Telegram")
class TelegramController(private val links: TelegramLinkService) {
    @GetMapping
    fun status(user: AuthenticatedUser): TelegramStatus = links.status(user.userId)

    @PostMapping("/link")
    @Operation(summary = "Genera el enlace de un solo uso para conectar Telegram (vence en 15 minutos)")
    fun link(user: AuthenticatedUser): TelegramLinkResponse = links.createLink(user.userId)

    @DeleteMapping("/link")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unlink(user: AuthenticatedUser) = links.unlink(user.userId)
}

/** Webhook de Telegram: solo acepta llamadas con el secreto registrado en `setWebhook`. */
@RestController
@RequestMapping("/api/v1/public/webhooks/telegram")
@Tag(name = "Telegram")
class TelegramWebhookController(
    private val properties: AppProperties,
    private val links: TelegramLinkService,
    private val objectMapper: ObjectMapper,
) {
    @PostMapping
    fun receive(
        @RequestHeader("X-Telegram-Bot-Api-Secret-Token", required = false) secret: String?,
        @RequestBody body: ByteArray,
    ): ResponseEntity<Void> {
        val expected = properties.telegram.webhookSecret
        if (!properties.telegram.enabled || secret == null ||
            !MessageDigest.isEqual(secret.toByteArray(), expected.toByteArray())
        ) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
        val message = runCatching { objectMapper.readTree(body).path("message") }.getOrNull()
        val chat = message?.path("chat")
        if (chat != null && chat.path("id").isIntegralNumber) {
            links.handleMessage(chat.path("id").asLong(), chat.path("type").asText(null), message.path("text").asText(null))
        }
        // Siempre 200: Telegram reintentaría sin fin un mensaje que no nos interesa.
        return ResponseEntity.ok().build()
    }
}
