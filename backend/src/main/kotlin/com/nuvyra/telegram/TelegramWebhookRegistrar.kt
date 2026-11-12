package com.nuvyra.telegram

import com.nuvyra.common.config.AppProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/** Al arrancar, apunta el bot a este servidor (solo con HTTPS y con el bot configurado). */
@Component
class TelegramWebhookRegistrar(private val properties: AppProperties, private val client: TelegramClient) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun register() {
        val base = properties.publicBaseUrl.trimEnd('/')
        if (!properties.telegram.enabled || !base.startsWith("https://")) return
        val ok = client.setWebhook("$base/api/v1/public/webhooks/telegram")
        if (ok) log.info("Webhook de Telegram registrado") else log.warn("No se pudo registrar el webhook de Telegram")
    }
}
