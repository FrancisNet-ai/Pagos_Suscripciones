package com.nuvyra.payments

import com.nuvyra.support.IntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class PaymentMethodIntegrationTest : IntegrationTest() {

    private fun body(holder: String = "Carlos Ramírez", phone: String = "912345678", enabled: Boolean = true) =
        """{"holderName":"$holder","phone":"$phone","enabled":$enabled}"""

    @Test
    fun `lista Yape y Plin aunque no esten configurados`() {
        val seller = registerSeller()
        mockMvc.perform(get("/api/v1/business/me/payment-methods").cookie(seller))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].method").value("YAPE"))
            .andExpect(jsonPath("$[0].configured").value(false))
            .andExpect(jsonPath("$[1].method").value("PLIN"))
    }

    @Test
    fun `Yape y Plin admiten titulares y numeros distintos`() {
        val seller = registerSeller()
        mockMvc.perform(put("/api/v1/business/me/payment-methods/yape").with(csrf()).cookie(seller).contentType(MediaType.APPLICATION_JSON).content(body()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.phone").value("+51912345678"))
        mockMvc.perform(put("/api/v1/business/me/payment-methods/PLIN").with(csrf()).cookie(seller).contentType(MediaType.APPLICATION_JSON).content(body("María López", "+51 998 877 665", false)))
            .andExpect(status().isOk)

        mockMvc.perform(get("/api/v1/business/me/payment-methods").cookie(seller))
            .andExpect(jsonPath("$[0].holderName").value("Carlos Ramírez"))
            .andExpect(jsonPath("$[0].enabled").value(true))
            .andExpect(jsonPath("$[1].holderName").value("María López"))
            .andExpect(jsonPath("$[1].phone").value("+51998877665"))
            .andExpect(jsonPath("$[1].enabled").value(false))
    }

    @Test
    fun `valida datos y metodo`() {
        val seller = registerSeller()
        mockMvc.perform(put("/api/v1/business/me/payment-methods/yape").with(csrf()).cookie(seller).contentType(MediaType.APPLICATION_JSON).content(body(holder = "", phone = "123")))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errors.length()").value(3))
        mockMvc.perform(put("/api/v1/business/me/payment-methods/tarjeta").with(csrf()).cookie(seller).contentType(MediaType.APPLICATION_JSON).content(body()))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `el QR exige configurar primero el metodo`() {
        val seller = registerSeller()
        val qr = MockMultipartFile("file", "qr.png", "image/png", pngBytes())
        mockMvc.perform(multipart("/api/v1/business/me/payment-methods/plin/qr").file(qr).with(csrf()).cookie(seller))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("PAYMENT_METHOD_NOT_CONFIGURED"))

        mockMvc.perform(put("/api/v1/business/me/payment-methods/plin").with(csrf()).cookie(seller).contentType(MediaType.APPLICATION_JSON).content(body()))
        mockMvc.perform(multipart("/api/v1/business/me/payment-methods/plin/qr").file(qr).with(csrf()).cookie(seller))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.qrImageUrl").value(org.hamcrest.Matchers.startsWith("/api/v1/public/media/")))
    }

    @Test
    fun `los datos de cobro de un vendedor no son visibles para otro`() {
        val sellerA = registerSeller()
        val sellerB = registerSeller()
        mockMvc.perform(put("/api/v1/business/me/payment-methods/yape").with(csrf()).cookie(sellerA).contentType(MediaType.APPLICATION_JSON).content(body()))
        mockMvc.perform(get("/api/v1/business/me/payment-methods").cookie(sellerB))
            .andExpect(jsonPath("$[0].configured").value(false))
            .andExpect(jsonPath("$[0].holderName").doesNotExist())
    }
}
