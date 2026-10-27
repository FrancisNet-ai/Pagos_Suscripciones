package com.nuvyra.support

import jakarta.servlet.http.Cookie
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.ResultActions
import java.util.UUID

data class Store(val seller: Cookie, val slug: String, val productId: String)

/** Arma una tienda lista para vender: Yape habilitado y un producto publicado. */
class StoreFixture(private val mockMvc: MockMvc) {

    fun idFrom(json: String, field: String = "id") = Regex("\"$field\":\"([^\"]+)\"").find(json)!!.groupValues[1]

    fun create(seller: Cookie, price: String = "15.00", capacity: Int? = null, whatsapp: String? = "912345678"): Store {
        mockMvc.perform(
            put("/api/v1/business/me/payment-methods/yape").with(csrf()).cookie(seller).contentType(MediaType.APPLICATION_JSON)
                .content("""{"holderName":"Carla Ramírez","phone":"911222333","enabled":true}"""),
        )
        val whatsappJson = whatsapp?.let { "\"$it\"" } ?: "null"
        mockMvc.perform(
            put("/api/v1/business/me").with(csrf()).cookie(seller).contentType(MediaType.APPLICATION_JSON)
                .content("""{"businessName":"Tienda de prueba","timeZone":"America/Lima","whatsappNumber":$whatsappJson}"""),
        )
        val product = mockMvc.perform(
            post("/api/v1/products").with(csrf()).cookie(seller).contentType(MediaType.APPLICATION_JSON).content(
                """{"name":"Servicio Premium","productType":"SUBSCRIPTION","price":$price,"durationDays":30,
                   "capacityEnabled":${capacity != null},"capacity":${capacity ?: "null"},"available":true}""",
            ),
        ).andReturn().response.contentAsString
        val productId = idFrom(product)
        publish(seller, productId)
        val slug = idFrom(mockMvc.perform(get("/api/v1/business/me").cookie(seller)).andReturn().response.contentAsString, "slug")
        return Store(seller, slug, productId)
    }

    fun publish(seller: Cookie, productId: String, status: String = "PUBLISHED") {
        mockMvc.perform(
            patch("/api/v1/products/$productId/status").with(csrf()).cookie(seller).contentType(MediaType.APPLICATION_JSON)
                .content("""{"publicationStatus":"$status"}"""),
        )
    }

    fun order(
        slug: String,
        productId: String,
        phone: String = "987654321",
        name: String = "Carlos Ramírez",
        method: String = "YAPE",
        extra: String = "",
        idempotencyKey: UUID? = null,
    ): ResultActions {
        val request = post("/api/v1/public/stores/$slug/orders").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("""{"productId":"$productId","customerName":"$name","phone":"$phone","paymentMethod":"$method","acceptTerms":true,"acceptDataProcessing":true$extra}""")
        idempotencyKey?.let { request.header("Idempotency-Key", it.toString()) }
        return mockMvc.perform(request)
    }

    fun placeOrder(store: Store, phone: String = "987654321"): String =
        idFrom(order(store.slug, store.productId, phone).andReturn().response.contentAsString, "publicToken")

    fun report(token: String, reference: String = "12345678", receipt: ByteArray? = null): ResultActions {
        val request = multipart("/api/v1/public/orders/$token/payment-report").param("transactionReference", reference).with(csrf())
        receipt?.let { request.file(MockMultipartFile("receipt", "voucher.png", "image/png", it)) }
        return mockMvc.perform(request)
    }

    /** Compra, reporta y verifica un pedido. Devuelve el id del pedido. */
    fun paidOrder(store: Store, phone: String = "987654321", productId: String = store.productId): String {
        val token = idFrom(order(store.slug, productId, phone).andReturn().response.contentAsString, "publicToken")
        report(token, reference = "R" + (System.nanoTime() % 100_000_000)).andReturn()
        val (orderId, paymentId) = latestPaymentId(store.seller)
        val status = mockMvc.perform(post("/api/v1/payments/$paymentId/verify").with(csrf()).cookie(store.seller)).andReturn().response.status
        check(status == 200) { "No se pudo verificar el pago ($status)" }
        return orderId
    }

    fun createProduct(seller: Cookie, json: String): String {
        val id = idFrom(mockMvc.perform(post("/api/v1/products").with(csrf()).cookie(seller).contentType(MediaType.APPLICATION_JSON).content(json)).andReturn().response.contentAsString)
        publish(seller, id)
        return id
    }

    /** Id del pago más reciente del pedido, visto desde el panel del vendedor. */
    fun latestPaymentId(seller: Cookie): Pair<String, String> {
        val list = mockMvc.perform(get("/api/v1/orders").cookie(seller)).andReturn().response.contentAsString
        val orderId = idFrom(list)
        val detail = mockMvc.perform(get("/api/v1/orders/$orderId").cookie(seller)).andReturn().response.contentAsString
        val paymentId = Regex("\"payments\":\\[\\{\"id\":\"([^\"]+)\"").find(detail)!!.groupValues[1]
        return orderId to paymentId
    }
}
