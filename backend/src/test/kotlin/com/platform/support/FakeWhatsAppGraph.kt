package com.platform.support

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger

/** Petición recibida por la Graph API simulada. */
data class GraphRequest(val method: String, val path: String, val authorization: String?, val body: String)

/**
 * Graph API de Meta simulada para las pruebas (nunca se llama a Meta de verdad). Responde como la
 * Cloud API: datos del número, suscripción de webhooks y envío de mensajes. El token «token-invalido»
 * recibe el error 190 y se pueden encolar respuestas de error.
 */
object FakeWhatsAppGraph {
    val requests = ConcurrentLinkedDeque<GraphRequest>()
    private val failures = ConcurrentLinkedDeque<Pair<Int, String>>()
    private val counter = AtomicInteger()

    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { ex ->
            val body = ex.requestBody.readBytes().toString(Charsets.UTF_8)
            val auth = ex.requestHeaders.getFirst("Authorization")
            requests.add(GraphRequest(ex.requestMethod, ex.requestURI.path, auth, body))
            val (status, response) = when {
                auth == "Bearer token-invalido" -> 401 to """{"error":{"message":"Invalid OAuth access token.","code":190}}"""
                ex.requestURI.path.endsWith("/messages") && failures.isNotEmpty() -> failures.poll()
                ex.requestURI.path.endsWith("/messages") -> 200 to """{"messaging_product":"whatsapp","messages":[{"id":"wamid.${counter.incrementAndGet()}"}]}"""
                ex.requestURI.path.endsWith("/subscribed_apps") -> 200 to """{"success":true}"""
                else -> 200 to """{"display_phone_number":"+51 955 000 111","verified_name":"Tienda de prueba","id":"1"}"""
            }
            val bytes = response.toByteArray()
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(status, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        start()
    }

    val baseUrl: String get() = "http://127.0.0.1:${server.address.port}"

    fun failNext(status: Int, message: String, code: Int = 100) {
        failures.add(status to """{"error":{"message":"$message","code":$code}}""")
    }

    fun messages(): List<GraphRequest> = requests.filter { it.path.endsWith("/messages") }

    fun reset() {
        requests.clear()
        failures.clear()
    }
}
