package com.nuvyra.telegram

import com.nuvyra.support.FakeTelegramApi
import com.nuvyra.support.IntegrationTest
import com.nuvyra.support.StoreFixture
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.random.Random

class TelegramIntegrationTest : IntegrationTest() {
    @Autowired lateinit var jdbc: JdbcTemplate

    private fun webhook(chatId: Long, text: String, secret: String? = FakeTelegramApi.SECRET, type: String = "private") = mockMvc.perform(
        post("/api/v1/public/webhooks/telegram").contentType(MediaType.APPLICATION_JSON)
            .apply { if (secret != null) header("X-Telegram-Bot-Api-Secret-Token", secret) }
            .content("""{"update_id":1,"message":{"message_id":1,"chat":{"id":$chatId,"type":"$type"},"text":"$text"}}"""),
    )

    /** Los avisos salen en segundo plano: reintenta la comprobación hasta 10 segundos. */
    private fun eventually(check: () -> Unit) {
        val deadline = System.currentTimeMillis() + 10_000
        while (true) {
            try {
                check()
                return
            } catch (e: AssertionError) {
                if (System.currentTimeMillis() > deadline) throw e
                Thread.sleep(100)
            }
        }
    }

    /** Conecta la cuenta como lo haría la persona: pide el enlace y «toca Iniciar» en Telegram. */
    private fun connect(session: Cookie, chatId: Long) {
        val url = mockMvc.perform(post("/api/v1/telegram/link").with(csrf()).cookie(session))
            .andExpect(status().isOk).andReturn().response.contentAsString
            .let { Regex("\"url\":\"([^\"]+)\"").find(it)!!.groupValues[1] }
        assertThat(url).startsWith("https://t.me/NuvyraPruebasBot?start=")
        webhook(chatId, "/start " + url.substringAfter("start=")).andExpect(status().isOk)
    }

    @Test
    fun `el vendedor conecta su Telegram con un codigo de un solo uso y puede desconectarlo`() {
        val seller = registerSeller()
        val chatId = Random.nextLong(1_000_000, 9_000_000)
        mockMvc.perform(get("/api/v1/telegram").cookie(seller))
            .andExpect(jsonPath("$.available").value(true)).andExpect(jsonPath("$.linked").value(false))
            .andExpect(jsonPath("$.botUsername").value("NuvyraPruebasBot"))

        val url = mockMvc.perform(post("/api/v1/telegram/link").with(csrf()).cookie(seller)).andReturn().response.contentAsString
        val code = Regex("start=([A-Za-z0-9_-]+)").find(url)!!.groupValues[1]
        // Sin el secreto de Telegram, el webhook no se acepta.
        webhook(chatId, "/start $code", secret = null).andExpect(status().isUnauthorized)
        webhook(chatId, "/start $code", secret = "otro-secreto-cualquiera-xx").andExpect(status().isUnauthorized)
        // Los grupos no se enlazan.
        webhook(chatId, "/start $code", type = "group").andExpect(status().isOk)
        mockMvc.perform(get("/api/v1/telegram").cookie(seller)).andExpect(jsonPath("$.linked").value(false))

        webhook(chatId, "/start $code").andExpect(status().isOk)
        mockMvc.perform(get("/api/v1/telegram").cookie(seller)).andExpect(jsonPath("$.linked").value(true))
        assertThat(FakeTelegramApi.messagesTo(chatId).last()).contains("Listo")
        // El código no se puede reutilizar.
        webhook(chatId + 1, "/start $code").andExpect(status().isOk)
        assertThat(FakeTelegramApi.messagesTo(chatId + 1).last()).contains("ya no es válido")
        assertThat(jdbc.queryForObject("select count(*) from telegram_link_codes", Int::class.java)).isEqualTo(0)

        mockMvc.perform(delete("/api/v1/telegram/link").with(csrf()).cookie(seller)).andExpect(status().isNoContent)
        mockMvc.perform(get("/api/v1/telegram").cookie(seller)).andExpect(jsonPath("$.linked").value(false))
    }

    @Test
    fun `avisa al titular de pedidos nuevos y pagos reportados, y al superadministrador de registros`() {
        val fx = StoreFixture(mockMvc)
        val seller = registerSeller()
        val sellerChat = Random.nextLong(10_000_000, 90_000_000)
        connect(seller, sellerChat)
        val adminChat = Random.nextLong(100_000_000, 900_000_000)
        connect(loginSuperAdmin(), adminChat)

        val store = fx.create(seller)
        fx.report(fx.placeOrder(store)).andExpect(status().isOk)
        eventually {
            val texts = FakeTelegramApi.messagesTo(sellerChat)
            // En pruebas la página no usa https, así que el botón «Ver pedido» se omite a propósito.
            assertThat(texts).anyMatch { it.contains("Nuevo pedido") && it.contains("Total: S/ 15.00") }
            assertThat(texts).anyMatch { it.contains("Pago por revisar") && it.contains("confírmalo solo si el dinero llegó") }
        }

        registerSeller(businessName = "Tienda Nueva Avisos")
        eventually {
            assertThat(FakeTelegramApi.messagesTo(adminChat)).anyMatch { it.contains("Nuevo vendedor registrado: Tienda Nueva Avisos") }
        }
        // Otro titular no recibe los avisos de esta tienda.
        assertThat(FakeTelegramApi.messagesTo(adminChat)).noneMatch { it.contains("Nuevo pedido") }
    }
}
