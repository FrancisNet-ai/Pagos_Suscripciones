package com.nuvyra.web

import com.nuvyra.support.IntegrationTest
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Test
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.nio.file.Files
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.zip.GZIPInputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Despliegue en un solo servicio: el backend sirve la compilación de Angular. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SpaWebConfigIntegrationTest : IntegrationTest() {
    @LocalServerPort
    private var port: Int = 0
    companion object {
        private val dist = Files.createTempDirectory("nuvyra-web").also {
            Files.writeString(it.resolve("index.html"), "<!doctype html><title>Nuvyra</title>")
            Files.writeString(it.resolve("main-ABC123.js"), "console.log('nuvyra')")
            Files.writeString(it.resolve("large-ABC123.js"), "console.log('nuvyra');".repeat(300))
            Files.createDirectory(it.resolve("art"))
            Files.writeString(it.resolve("art/cover.svg"), "<svg xmlns=\"http://www.w3.org/2000/svg\"/>")
            Files.writeString(it.resolve("loading-mark.svg"), "<svg xmlns=\"http://www.w3.org/2000/svg\"/>")
            Files.createDirectory(it.resolve("media"))
            Files.write(it.resolve("media/font-ABC123.woff2"), byteArrayOf(1, 2, 3))
        }

        @JvmStatic
        @DynamicPropertySource
        fun staticDir(registry: DynamicPropertyRegistry) {
            registry.add("app.web.static-dir") { dist.toString() }
        }
    }

    @Test
    fun `sirve la aplicacion y deja que Angular resuelva sus rutas`() {
        // La raíz se reenvía a index.html (MockMvc no ejecuta el reenvío: solo lo registra).
        mockMvc.perform(get("/")).andExpect(forwardedUrl("/index.html"))
        mockMvc.perform(get("/index.html"))
            .andExpect(status().isOk).andExpect(content().string(containsString("<title>Nuvyra</title>")))
            .andExpect(header().string("Cache-Control", containsString("no-cache")))
            .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
        mockMvc.perform(get("/panel/ventas")).andExpect(status().isOk).andExpect(content().string(containsString("Nuvyra")))
        mockMvc.perform(get("/tienda/mi-tienda")).andExpect(status().isOk)
        mockMvc.perform(head("/index.html")).andExpect(status().isOk)
    }

    @Test
    fun `los archivos con hash se guardan en cache y lo que no existe da 404`() {
        mockMvc.perform(get("/main-ABC123.js"))
            .andExpect(status().isOk).andExpect(header().string("Cache-Control", containsString("immutable")))
        mockMvc.perform(get("/media/font-ABC123.woff2"))
            .andExpect(status().isOk).andExpect(content().bytes(byteArrayOf(1, 2, 3)))
            .andExpect(header().string("Cache-Control", containsString("immutable")))
        mockMvc.perform(get("/no-existe.png")).andExpect(status().isNotFound)
        // La API no cae en index.html ni recibe la CSP de la página.
        mockMvc.perform(get("/api/v1/public/no-existe"))
            .andExpect(status().isNotFound).andExpect(header().string("Content-Security-Policy", nullValue()))
    }

    @Test
    fun `reutiliza el arte publico sin cachear el HTML ni las respuestas de sesion`() {
        listOf("/art/cover.svg", "/loading-mark.svg").forEach { path ->
            mockMvc.perform(get(path)).andExpect(status().isOk)
                .andExpect(header().string("Cache-Control", containsString("max-age=3600")))
                .andExpect(header().string("Cache-Control", containsString("stale-while-revalidate=86400")))
        }
        mockMvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized)
            .andExpect(header().string("Cache-Control", containsString("no-store")))
    }

    @Test
    fun `el servidor comprime JavaScript y respeta los clientes que no piden gzip`() {
        val client = HttpClient.newHttpClient()
        val uri = URI("http://localhost:$port/large-ABC123.js")
        val compressed = client.send(HttpRequest.newBuilder(uri).header("Accept-Encoding", "gzip").build(), HttpResponse.BodyHandlers.ofByteArray())
        assertEquals(200, compressed.statusCode())
        assertEquals("gzip", compressed.headers().firstValue("Content-Encoding").orElse(null))
        val plain = client.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofByteArray())
        assertEquals(200, plain.statusCode())
        assertTrue(compressed.body().size < plain.body().size / 5)
        assertTrue(GZIPInputStream(compressed.body().inputStream()).readBytes().contentEquals(plain.body()))
    }
}
