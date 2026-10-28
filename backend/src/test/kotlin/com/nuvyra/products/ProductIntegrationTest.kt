package com.nuvyra.products

import com.nuvyra.capacity.CapacityService
import com.nuvyra.support.IntegrationTest
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.util.UUID

class ProductIntegrationTest : IntegrationTest() {
    @Autowired
    lateinit var capacityService: CapacityService

    private fun productJson(
        name: String = "Servicio Premium",
        type: String = "SUBSCRIPTION",
        price: String = "15.00",
        duration: Int? = 30,
        capacityEnabled: Boolean = false,
        capacity: Int? = null,
        category: String = "Entretenimiento",
    ) = """{"name":"$name","description":"Acceso mensual","category":"$category","productType":"$type","price":$price,
        "durationDays":${duration ?: "null"},"capacityEnabled":$capacityEnabled,"capacity":${capacity ?: "null"},"available":true}"""

    private fun create(seller: Cookie, json: String = productJson()): String =
        mockMvc.perform(post("/api/v1/products").with(csrf()).cookie(seller).contentType(MediaType.APPLICATION_JSON).content(json))
            .andExpect(status().isCreated)
            .andReturn().response.contentAsString.let { Regex("\"id\":\"([^\"]+)\"").find(it)!!.groupValues[1] }

    @Test
    fun `crea un producto como borrador con precio en soles`() {
        val seller = registerSeller()
        mockMvc.perform(post("/api/v1/products").with(csrf()).cookie(seller).contentType(MediaType.APPLICATION_JSON).content(productJson(price = "15.5")))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.publicationStatus").value("DRAFT"))
            .andExpect(jsonPath("$.price").value(15.50))
            .andExpect(jsonPath("$.currency").value("PEN"))
            .andExpect(jsonPath("$.availability.capacityEnabled").value(false))
            .andExpect(jsonPath("$.availability.available").doesNotExist())
    }

    @Test
    fun `valida reglas del producto`() {
        val seller = registerSeller()
        mockMvc.perform(post("/api/v1/products").with(csrf()).cookie(seller).contentType(MediaType.APPLICATION_JSON).content(productJson(duration = null)))
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.errors[0].field").value("durationDays"))
        mockMvc.perform(post("/api/v1/products").with(csrf()).cookie(seller).contentType(MediaType.APPLICATION_JSON).content(productJson(capacityEnabled = true)))
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.errors[0].field").value("capacity"))
        mockMvc.perform(post("/api/v1/products").with(csrf()).cookie(seller).contentType(MediaType.APPLICATION_JSON).content(productJson(price = "10.999")))
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.errors[0].field").value("price"))
        mockMvc.perform(post("/api/v1/products").with(csrf()).cookie(seller).contentType(MediaType.APPLICATION_JSON).content(productJson(price = "0")))
            .andExpect(status().isBadRequest)
        // Un producto de pago único no requiere duración.
        create(seller, productJson(type = "ONE_TIME", duration = null))
    }

    @Test
    fun `busca, filtra y pagina solo productos del negocio`() {
        val seller = registerSeller()
        create(seller, productJson(name = "Plan Familiar"))
        create(seller, productJson(name = "Curso de Excel", category = "Educación"))
        val archived = create(seller, productJson(name = "Plan Antiguo"))
        mockMvc.perform(patch("/api/v1/products/$archived/status").with(csrf()).cookie(seller).contentType(MediaType.APPLICATION_JSON).content("""{"publicationStatus":"ARCHIVED"}"""))
            .andExpect(status().isOk)

        mockMvc.perform(get("/api/v1/products").cookie(seller))
            .andExpect(jsonPath("$.totalItems").value(2))
        mockMvc.perform(get("/api/v1/products").cookie(seller).param("query", "PLAN"))
            .andExpect(jsonPath("$.totalItems").value(1))
            .andExpect(jsonPath("$.items[0].name").value("Plan Familiar"))
        mockMvc.perform(get("/api/v1/products").cookie(seller).param("query", "educa"))
            .andExpect(jsonPath("$.items[0].name").value("Curso de Excel"))
        mockMvc.perform(get("/api/v1/products").cookie(seller).param("filter", "ARCHIVED"))
            .andExpect(jsonPath("$.totalItems").value(1))
        mockMvc.perform(get("/api/v1/products").cookie(seller).param("query", "%"))
            .andExpect(jsonPath("$.totalItems").value(0))
        mockMvc.perform(get("/api/v1/products").cookie(seller).param("size", "1"))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.totalPages").value(2))
    }

    @Test
    fun `publica, despublica y edita un producto`() {
        val seller = registerSeller()
        val id = create(seller)
        mockMvc.perform(patch("/api/v1/products/$id/status").with(csrf()).cookie(seller).contentType(MediaType.APPLICATION_JSON).content("""{"publicationStatus":"PUBLISHED"}"""))
            .andExpect(jsonPath("$.publicationStatus").value("PUBLISHED"))
        mockMvc.perform(put("/api/v1/products/$id").with(csrf()).cookie(seller).contentType(MediaType.APPLICATION_JSON).content(productJson(name = "Servicio Premium Plus", price = "20.00")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Servicio Premium Plus"))
            .andExpect(jsonPath("$.price").value(20.0))
            .andExpect(jsonPath("$.publicationStatus").value("PUBLISHED"))
        mockMvc.perform(
            multipart("/api/v1/products/$id/image").file(MockMultipartFile("file", "p.png", "image/png", pngBytes())).with(csrf()).cookie(seller),
        ).andExpect(jsonPath("$.imageUrl").exists())
    }

    @Test
    fun `una foto grande guarda una version reducida para el catalogo`() {
        val seller = registerSeller()
        val id = create(seller)
        // Degradado con algo de ruido: parecido a una foto y por debajo del límite de 2 MB.
        val image = java.awt.image.BufferedImage(1200, 800, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val random = java.util.Random(3)
        for (x in 0 until 1200) for (y in 0 until 800) {
            image.setRGB(x, y, java.awt.Color((x * 255) / 1200, (y * 255) / 800, 120 + random.nextInt(16)).rgb)
        }
        val photo = java.io.ByteArrayOutputStream().also { javax.imageio.ImageIO.write(image, "png", it) }.toByteArray()
        val body = mockMvc.perform(
            multipart("/api/v1/products/$id/image").file(MockMultipartFile("file", "foto.png", "image/png", photo)).with(csrf()).cookie(seller),
        ).andExpect(status().isOk).andReturn().response.contentAsString
        val url = Regex("\"imageUrl\":\"([^\"]+)\"").find(body)!!.groupValues[1]

        val original = mockMvc.perform(get(url)).andExpect(status().isOk).andReturn().response
        val small = mockMvc.perform(get(url).param("size", "sm")).andExpect(status().isOk).andReturn().response
        org.assertj.core.api.Assertions.assertThat(original.contentType).isEqualTo("image/png")
        org.assertj.core.api.Assertions.assertThat(small.contentType).isEqualTo("image/jpeg")
        org.assertj.core.api.Assertions.assertThat(small.contentAsByteArray.size).isLessThan(original.contentAsByteArray.size / 4)
        // Un tamaño desconocido devuelve el original.
        org.assertj.core.api.Assertions.assertThat(mockMvc.perform(get(url).param("size", "xl")).andReturn().response.contentType).isEqualTo("image/png")
    }

    @Test
    fun `un vendedor no puede ver ni modificar productos de otro`() {
        val sellerA = registerSeller()
        val sellerB = registerSeller()
        val productA = create(sellerA)

        mockMvc.perform(get("/api/v1/products/$productA").cookie(sellerB)).andExpect(status().isNotFound)
        mockMvc.perform(put("/api/v1/products/$productA").with(csrf()).cookie(sellerB).contentType(MediaType.APPLICATION_JSON).content(productJson(price = "1.00")))
            .andExpect(status().isNotFound)
        mockMvc.perform(patch("/api/v1/products/$productA/status").with(csrf()).cookie(sellerB).contentType(MediaType.APPLICATION_JSON).content("""{"publicationStatus":"ARCHIVED"}"""))
            .andExpect(status().isNotFound)
        mockMvc.perform(get("/api/v1/products").cookie(sellerB)).andExpect(jsonPath("$.totalItems").value(0))

        mockMvc.perform(get("/api/v1/products/$productA").cookie(sellerA))
            .andExpect(jsonPath("$.price").value(15.0))
            .andExpect(jsonPath("$.publicationStatus").value("DRAFT"))
    }

    @Test
    fun `no permite reducir la capacidad por debajo de los cupos en uso`() {
        val seller = registerSeller()
        val id = create(seller, productJson(capacityEnabled = true, capacity = 3))
        val businessId = businessIdOf(seller)
        repeat(2) { capacityService.reserve(businessId, UUID.fromString(id), Duration.ofMinutes(30)) }

        mockMvc.perform(get("/api/v1/products/$id").cookie(seller))
            .andExpect(jsonPath("$.availability.reserved").value(2))
            .andExpect(jsonPath("$.availability.available").value(1))
        mockMvc.perform(put("/api/v1/products/$id").with(csrf()).cookie(seller).contentType(MediaType.APPLICATION_JSON).content(productJson(capacityEnabled = true, capacity = 1)))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("CAPACITY_BELOW_USAGE"))
        mockMvc.perform(put("/api/v1/products/$id").with(csrf()).cookie(seller).contentType(MediaType.APPLICATION_JSON).content(productJson(capacityEnabled = true, capacity = 2)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.availability.soldOut").value(true))
    }

    private fun businessIdOf(seller: Cookie): UUID =
        mockMvc.perform(get("/api/v1/business/me").cookie(seller)).andReturn().response.contentAsString
            .let { UUID.fromString(Regex("\"id\":\"([^\"]+)\"").find(it)!!.groupValues[1]) }
}
