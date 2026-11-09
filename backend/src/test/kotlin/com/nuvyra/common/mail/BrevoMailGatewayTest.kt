package com.nuvyra.common.mail

import com.nuvyra.auth.MailPasswordResetNotifier
import com.nuvyra.staff.MailStaffInvitationNotifier
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.net.InetSocketAddress
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList

/** Brevo simulado: comprueba la ruta, la cabecera `api-key` y el cuerpo que se envía. */
class BrevoMailGatewayTest {
    private class Request(val path: String, val method: String, val apiKey: String?, val body: String)

    private val requests = CopyOnWriteArrayList<Request>()

    @Volatile
    private var status = 201

    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { ex ->
            requests.add(Request(ex.requestURI.path, ex.requestMethod, ex.requestHeaders.getFirst("api-key"), String(ex.requestBody.readBytes())))
            val response = if (status == 201) """{"messageId":"<id@brevo>"}""" else """{"code":"unauthorized","message":"Key not found"}"""
            val bytes = response.toByteArray()
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(status, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        start()
    }

    private val gateway = BrevoMailGateway(
        "http://127.0.0.1:${server.address.port}/", "xkeysib-prueba", "soporte@ejemplo.pe", "Nuvyra", Duration.ofSeconds(5),
    )

    @AfterEach
    fun stop() = server.stop(0)

    @Test
    fun `envia el correo de recuperacion con remitente, destinatario y texto`() {
        MailPasswordResetNotifier(gateway).send("ana@ejemplo.pe", "Ana Pérez", "https://nuvyra.test/restablecer?token=abc")

        val request = requests.single()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/v3/smtp/email")
        assertThat(request.apiKey).isEqualTo("xkeysib-prueba")
        val json = JsonMapper.builder().build().readTree(request.body)
        assertThat(json["sender"]["email"].asString()).isEqualTo("soporte@ejemplo.pe")
        assertThat(json["sender"]["name"].asString()).isEqualTo("Nuvyra")
        assertThat(json["to"][0]["email"].asString()).isEqualTo("ana@ejemplo.pe")
        assertThat(json["to"][0]["name"].asString()).isEqualTo("Ana Pérez")
        assertThat(json["subject"].asString()).isEqualTo("Restablece tu contraseña de Nuvyra")
        assertThat(json["textContent"].asString()).contains("https://nuvyra.test/restablecer?token=abc")
    }

    @Test
    fun `un rechazo de Brevo se propaga como error`() {
        status = 401
        assertThatThrownBy { gateway.send("ana@ejemplo.pe", "Ana", "Asunto", "Texto") }.isInstanceOf(Exception::class.java)
    }

    @Test
    fun `la invitacion informa si el correo no salio`() {
        val notifier = MailStaffInvitationNotifier(gateway)
        assertThat(notifier.send("luis@ejemplo.pe", "Luis", "Tienda Sol", "https://nuvyra.test/invitacion?token=x")).isTrue()
        status = 500
        assertThat(notifier.send("luis@ejemplo.pe", "Luis", "Tienda Sol", "https://nuvyra.test/invitacion?token=y")).isFalse()
    }
}
