package com.platform.businesses

import com.platform.support.IntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class BusinessSettingsIntegrationTest : IntegrationTest() {

    private fun settingsJson(name: String = "Digital Streaming", color: String = "#1a3d63", whatsapp: String = "912 345 678", tz: String = "America/Lima") =
        """{"businessName":"$name","description":"Servicios digitales","primaryColor":"$color","whatsappNumber":"$whatsapp","timeZone":"$tz"}"""

    @Test
    fun `actualiza datos y personalizacion del negocio`() {
        val seller = registerSeller()
        mockMvc.perform(put("/api/v1/business/me").with(csrf()).cookie(seller).contentType(MediaType.APPLICATION_JSON).content(settingsJson()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.businessName").value("Digital Streaming"))
            .andExpect(jsonPath("$.primaryColor").value("#1A3D63"))
            .andExpect(jsonPath("$.whatsappNumber").value("+51912345678"))
    }

    @Test
    fun `rechaza color, zona horaria y WhatsApp invalidos`() {
        val seller = registerSeller()
        mockMvc.perform(put("/api/v1/business/me").with(csrf()).cookie(seller).contentType(MediaType.APPLICATION_JSON).content(settingsJson(color = "azul", whatsapp = "123")))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errors.length()").value(2))
        mockMvc.perform(put("/api/v1/business/me").with(csrf()).cookie(seller).contentType(MediaType.APPLICATION_JSON).content(settingsJson(tz = "Marte/Olympus")))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errors[0].field").value("timeZone"))
    }

    @Test
    fun `cambia el slug y rechaza slugs ocupados, reservados o mal formados`() {
        val sellerA = registerSeller(businessName = "Slug Negocio A")
        val sellerB = registerSeller(businessName = "Slug Negocio B")
        val newSlug = "mi-tienda-${System.nanoTime() % 100000}"

        mockMvc.perform(get("/api/v1/business/me/slug-availability").cookie(sellerA).param("slug", newSlug))
            .andExpect(jsonPath("$.available").value(true))
        mockMvc.perform(put("/api/v1/business/me/slug").with(csrf()).cookie(sellerA).contentType(MediaType.APPLICATION_JSON).content("""{"slug":"$newSlug"}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.slug").value(newSlug))

        mockMvc.perform(get("/api/v1/business/me/slug-availability").cookie(sellerB).param("slug", newSlug))
            .andExpect(jsonPath("$.available").value(false))
        mockMvc.perform(put("/api/v1/business/me/slug").with(csrf()).cookie(sellerB).contentType(MediaType.APPLICATION_JSON).content("""{"slug":"$newSlug"}"""))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("SLUG_TAKEN"))
        mockMvc.perform(put("/api/v1/business/me/slug").with(csrf()).cookie(sellerB).contentType(MediaType.APPLICATION_JSON).content("""{"slug":"admin"}"""))
            .andExpect(status().isBadRequest)
        mockMvc.perform(put("/api/v1/business/me/slug").with(csrf()).cookie(sellerB).contentType(MediaType.APPLICATION_JSON).content("""{"slug":"Con Espacios"}"""))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `sube, sirve y elimina el logotipo validando el archivo`() {
        val seller = registerSeller()
        val logoUrl = mockMvc.perform(
            multipart("/api/v1/business/me/logo").file(MockMultipartFile("file", "logo.png", "image/png", pngBytes())).with(csrf()).cookie(seller),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString.let { Regex("\"logoUrl\":\"([^\"]+)\"").find(it)!!.groupValues[1] }
        assertThat(logoUrl).matches("/api/v1/public/media/[0-9a-f-]{36}\\.png")

        mockMvc.perform(get(logoUrl))
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Type", "image/png"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))

        // Un archivo HTML disfrazado de PNG se rechaza.
        mockMvc.perform(
            multipart("/api/v1/business/me/cover").file(MockMultipartFile("file", "x.png", "image/png", "<html></html>".toByteArray())).with(csrf()).cookie(seller),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_IMAGE"))

        mockMvc.perform(delete("/api/v1/business/me/logo").with(csrf()).cookie(seller))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.logoUrl").doesNotExist())
        mockMvc.perform(get(logoUrl)).andExpect(status().isNotFound)
    }

    @Test
    fun `la ruta de archivos publicos no permite recorrer directorios`() {
        mockMvc.perform(get("/api/v1/public/media/..%2F..%2Fetc%2Fpasswd")).andExpect(status().is4xxClientError)
        mockMvc.perform(get("/api/v1/public/media/no-existe.png")).andExpect(status().isNotFound)
    }
}
