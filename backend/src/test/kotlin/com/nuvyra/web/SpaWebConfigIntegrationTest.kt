package com.nuvyra.web

import com.nuvyra.support.IntegrationTest
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

/** Despliegue en un solo servicio: el backend sirve la compilación de Angular. */
class SpaWebConfigIntegrationTest : IntegrationTest() {
    companion object {
        private val dist = Files.createTempDirectory("nuvyra-web").also {
            Files.writeString(it.resolve("index.html"), "<!doctype html><title>Nuvyra</title>")
            Files.writeString(it.resolve("main-ABC123.js"), "console.log('nuvyra')")
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
        mockMvc.perform(get("/no-existe.png")).andExpect(status().isNotFound)
        // La API no cae en index.html ni recibe la CSP de la página.
        mockMvc.perform(get("/api/v1/public/no-existe"))
            .andExpect(status().isNotFound).andExpect(header().string("Content-Security-Policy", nullValue()))
    }
}
