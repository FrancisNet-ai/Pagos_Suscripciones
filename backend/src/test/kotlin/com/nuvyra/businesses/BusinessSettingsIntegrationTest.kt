package com.nuvyra.businesses

import com.nuvyra.support.IntegrationTest
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

    private fun linksJson(links: String) =
        """{"businessName":"Tienda Redes","timeZone":"America/Lima",$links}"""

    @Test
    fun `guarda las redes del vendedor normalizadas y las muestra en la tienda publica`() {
        val email = uniqueEmail()
        val seller = registerSeller(email)
        val slug = mockMvc.perform(
            put("/api/v1/business/me").with(csrf()).cookie(seller).contentType(MediaType.APPLICATION_JSON).content(
                linksJson(""""instagramUrl":"@tecno.siam","facebookUrl":"facebook.com/tecnosiam","tiktokUrl":"https://www.tiktok.com/@tecnosiam?lang=es","websiteUrl":" """"),
            ),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.socialLinks.instagram").value("https://www.instagram.com/tecno.siam"))
            .andExpect(jsonPath("$.socialLinks.facebook").value("https://facebook.com/tecnosiam"))
            .andExpect(jsonPath("$.socialLinks.tiktok").value("https://www.tiktok.com/@tecnosiam?lang=es"))
            .andExpect(jsonPath("$.socialLinks.website").doesNotExist())
            .andReturn().response.contentAsString.let { Regex("\"slug\":\"([^\"]+)\"").find(it)!!.groupValues[1] }

        mockMvc.perform(get("/api/v1/public/stores/$slug"))
            .andExpect(jsonPath("$.socialLinks.instagram").value("https://www.instagram.com/tecno.siam"))
            .andExpect(jsonPath("$.socialLinks.website").doesNotExist())

        // Un ícono de Instagram nunca lleva a otro sitio, ni a enlaces con usuario o sin dominio.
        for (bad in listOf(""""instagramUrl":"https://evil.example/instagram.com"""", """"facebookUrl":"https://user@facebook.com.evil.example"""",
            """"tiktokUrl":"javascript:alert(1)"""", """"websiteUrl":"localhost"""")) {
            mockMvc.perform(put("/api/v1/business/me").with(csrf()).cookie(seller).contentType(MediaType.APPLICATION_JSON).content(linksJson(bad)))
                .andExpect(status().isBadRequest)
        }
        // Vaciar un campo quita el ícono.
        mockMvc.perform(put("/api/v1/business/me").with(csrf()).cookie(seller).contentType(MediaType.APPLICATION_JSON)
            .content(linksJson(""""instagramUrl":"""""))).andExpect(jsonPath("$.socialLinks.instagram").doesNotExist())
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
