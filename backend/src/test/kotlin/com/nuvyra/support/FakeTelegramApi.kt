package com.nuvyra.support

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedDeque

/** Petición recibida por la Bot API de Telegram simulada. */
data class TelegramRequest(val path: String, val body: String)

/** Bot API de Telegram simulada para las pruebas: responde `ok` y guarda lo que se envió. */
object FakeTelegramApi {
    const val TOKEN = "123456:token-de-prueba"
    const val SECRET = "secreto-de-prueba-0123456789"
    val requests = ConcurrentLinkedDeque<TelegramRequest>()

    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { ex ->
            requests.add(TelegramRequest(ex.requestURI.path, ex.requestBody.readBytes().toString(Charsets.UTF_8)))
            val bytes = """{"ok":true,"result":true}""".toByteArray()
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(200, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        start()
    }

    val baseUrl: String get() = "http://127.0.0.1:${server.address.port}"

    fun messagesTo(chatId: Long): List<String> =
        requests.filter { it.path.endsWith("/sendMessage") && it.body.contains("\"chat_id\":$chatId") }.map { it.body }
}
