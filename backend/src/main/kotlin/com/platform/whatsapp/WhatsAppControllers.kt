package com.platform.whatsapp

import com.platform.security.CurrentBusiness
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/business/me/whatsapp")
@Tag(name = "WhatsApp Business")
class WhatsAppIntegrationController(private val service: WhatsAppIntegrationService) {

    @GetMapping
    @Operation(summary = "Estado de la conexión (el token nunca se devuelve)")
    fun get(current: CurrentBusiness) = service.get(current.businessId)

    @PutMapping
    @Operation(summary = "Guarda la conexión y la verifica contra la Graph API antes de habilitar el envío")
    fun save(current: CurrentBusiness, @Valid @RequestBody body: WhatsAppIntegrationRequest) = service.save(current.businessId, current.userId, body)

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Desconecta el número y elimina el token guardado")
    fun remove(current: CurrentBusiness) = service.remove(current.businessId, current.userId)
}

/**
 * Webhook público de Meta. GET: verificación del alta con el token de la plataforma.
 * POST: eventos firmados con `X-Hub-Signature-256`; sin firma válida se rechazan.
 */
@RestController
@RequestMapping("/api/v1/public/webhooks/whatsapp")
@Tag(name = "WhatsApp Business")
class WhatsAppWebhookController(private val service: WhatsAppWebhookService) {

    @GetMapping
    fun verify(
        @RequestParam("hub.mode", required = false) mode: String?,
        @RequestParam("hub.verify_token", required = false) token: String?,
        @RequestParam("hub.challenge", required = false) challenge: String?,
    ): ResponseEntity<String> =
        if (service.verifySubscription(mode, token) && challenge != null) {
            ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(challenge)
        } else {
            ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun receive(@RequestBody body: ByteArray, @RequestHeader("X-Hub-Signature-256", required = false) signature: String?): ResponseEntity<Void> {
        if (!service.validSignature(body, signature)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        return if (service.handle(body)) ResponseEntity.ok().build() else ResponseEntity.internalServerError().build()
    }
}
