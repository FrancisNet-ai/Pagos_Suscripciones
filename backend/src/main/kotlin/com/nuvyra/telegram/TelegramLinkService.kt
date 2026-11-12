package com.nuvyra.telegram

import com.nuvyra.common.config.AppProperties
import com.nuvyra.common.error.ApiException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration
import java.util.Base64
import java.util.UUID

data class TelegramStatus(val available: Boolean, val linked: Boolean, val botUsername: String?)
data class TelegramLinkResponse(val url: String)

/**
 * Enlace entre una cuenta de Nuvyra y su chat privado con el bot. La persona toca «Conectar», Telegram se abre
 * con `/start <código>` y el webhook guarda su chat. El código es de un solo uso, dura 15 minutos y solo se
 * guarda su hash.
 */
@Service
class TelegramLinkService(
    private val properties: AppProperties,
    private val jdbc: JdbcTemplate,
    private val client: TelegramClient,
    private val clock: Clock,
) {
    private val random = SecureRandom()

    fun status(userId: UUID): TelegramStatus {
        val enabled = properties.telegram.enabled
        val linked = enabled && jdbc.queryForObject("select exists (select 1 from telegram_links where user_id = ?)", Boolean::class.java, userId)!!
        return TelegramStatus(enabled, linked, properties.telegram.botUsername.takeIf { enabled })
    }

    @Transactional
    fun createLink(userId: UUID): TelegramLinkResponse {
        if (!properties.telegram.enabled) throw ApiException.conflict("TELEGRAM_UNAVAILABLE", "Los avisos por Telegram no están disponibles por ahora.")
        val code = ByteArray(18).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        jdbc.update("delete from telegram_link_codes where user_id = ? or expires_at < ?", userId, Timestamp.from(clock.instant()))
        jdbc.update(
            "insert into telegram_link_codes (code_hash, user_id, expires_at) values (?, ?, ?)",
            hash(code), userId, Timestamp.from(clock.instant().plus(CODE_TTL)),
        )
        return TelegramLinkResponse("https://t.me/${properties.telegram.botUsername}?start=$code")
    }

    @Transactional
    fun unlink(userId: UUID) {
        jdbc.update("delete from telegram_links where user_id = ?", userId)
    }

    /** Mensaje privado recibido por el webhook: `/start <código>` enlaza y `/stop` desenlaza. */
    @Transactional
    fun handleMessage(chatId: Long, chatType: String?, text: String?) {
        if (chatType != "private") return
        val command = text?.trim().orEmpty()
        when {
            command.startsWith("/start ") -> {
                val code = command.removePrefix("/start ").trim()
                val userId = jdbc.query(
                    "delete from telegram_link_codes where code_hash = ? and expires_at > ? returning user_id",
                    { rs, _ -> rs.getObject(1, UUID::class.java) }, hash(code), Timestamp.from(clock.instant()),
                ).firstOrNull()
                if (userId == null) {
                    client.sendMessage(chatId, "Este enlace ya no es válido. Vuelve a Nuvyra y toca «Conectar Telegram» otra vez.")
                    return
                }
                // Un chat pertenece a una sola cuenta: si ya estaba enlazado a otra, pasa a esta.
                jdbc.update("delete from telegram_links where chat_id = ? and user_id <> ?", chatId, userId)
                jdbc.update(
                    "insert into telegram_links (user_id, chat_id, linked_at) values (?, ?, ?) on conflict (user_id) do update set chat_id = excluded.chat_id, linked_at = excluded.linked_at",
                    userId, chatId, Timestamp.from(clock.instant()),
                )
                client.sendMessage(chatId, "✅ ¡Listo! Tu cuenta de Nuvyra quedó conectada. Aquí te avisaremos de pedidos nuevos y pagos por revisar.\n\nPara dejar de recibir avisos escribe /stop.")
            }
            command == "/stop" -> {
                val removed = jdbc.update("delete from telegram_links where chat_id = ?", chatId)
                client.sendMessage(chatId, if (removed > 0) "Listo, ya no recibirás avisos de Nuvyra. Puedes volver a conectarte desde tu panel." else "Este chat no estaba conectado a ninguna cuenta de Nuvyra.")
            }
            else -> client.sendMessage(chatId, "Hola 👋 Soy el bot de avisos de Nuvyra. Para conectarlo, entra a tu panel de Nuvyra y toca «Conectar Telegram».")
        }
    }

    fun chatFor(userId: UUID): Long? =
        jdbc.query("select chat_id from telegram_links where user_id = ?", { rs, _ -> rs.getLong(1) }, userId).firstOrNull()

    private fun hash(code: String): String =
        MessageDigest.getInstance("SHA-256").digest(code.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        val CODE_TTL: Duration = Duration.ofMinutes(15)
    }
}
