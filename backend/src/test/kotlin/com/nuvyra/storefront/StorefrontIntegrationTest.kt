package com.nuvyra.storefront

import com.nuvyra.support.IntegrationTest
import com.nuvyra.support.StoreFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

class StorefrontIntegrationTest : IntegrationTest() {
    @Autowired lateinit var jdbc: JdbcTemplate
    lateinit var fx: StoreFixture

    @BeforeEach
    fun setUp() {
        fx = StoreFixture(mockMvc)
    }

    @Test
    fun `la tienda publica muestra solo productos publicados del negocio, sin datos internos`() {
        val store = fx.create(registerSeller(businessName = "Tienda Pública Uno"))
        val draft = fx.idFrom(
            mockMvc.perform(
                post("/api/v1/products").with(csrf()).cookie(store.seller).contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"Borrador","productType":"ONE_TIME","price":5,"capacityEnabled":false,"available":true}"""),
            ).andReturn().response.contentAsString,
        )
        val other = fx.create(registerSeller(businessName = "Otra Tienda"))

        val body = mockMvc.perform(get("/api/v1/public/stores/${store.slug}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.businessName").value("Tienda de prueba"))
            .andExpect(jsonPath("$.acceptingOrders").value(true))
            .andExpect(jsonPath("$.paymentMethods[0].method").value("YAPE"))
            .andReturn().response.contentAsString
        assertThat(body).doesNotContain("uniqueCode", "NEG-", "ownerId", "911222333")

        mockMvc.perform(get("/api/v1/public/stores/${store.slug}/products"))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(store.productId))
            .andExpect(jsonPath("$[0].availableSlots").doesNotExist())
        mockMvc.perform(get("/api/v1/public/stores/${store.slug}/products/$draft")).andExpect(status().isNotFound)
        // Un producto de otro negocio no se encuentra a través de este slug.
        mockMvc.perform(get("/api/v1/public/stores/${store.slug}/products/${other.productId}")).andExpect(status().isNotFound)
        mockMvc.perform(get("/api/v1/public/stores/no-existe-esta-tienda")).andExpect(status().isNotFound)
    }

    @Test
    fun `crea el pedido sin registro con precio del servidor, numero legible y reserva de cupo`() {
        val store = fx.create(registerSeller(), price = "15.00", capacity = 3)
        val response = fx.order(store.slug, store.productId, phone = "+51 987 111 222", extra = ""","price":0.01,"amount":0.01""")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.orderNumber").value(org.hamcrest.Matchers.matchesPattern("ORD-\\d{4}-000001")))
            .andReturn().response.contentAsString
        val token = fx.idFrom(response, "publicToken")

        mockMvc.perform(get("/api/v1/public/orders/$token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.amount").value(15.0))
            .andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"))
            .andExpect(jsonPath("$.statusLabel").value("Pendiente de pago"))
            .andExpect(jsonPath("$.customerPhoneMasked").value("*** *** 222"))
            .andExpect(jsonPath("$.payment.instructions.holderName").value("Carla Ramírez"))
            .andExpect(jsonPath("$.payment.instructions.phone").value("+51911222333"))
            .andExpect(jsonPath("$.payment.instructions.automaticVerification").value(false))
            .andExpect(jsonPath("$.reservedUntil").exists())
            .andExpect(jsonPath("$.canReportPayment").value(true))

        mockMvc.perform(get("/api/v1/public/stores/${store.slug}/products"))
            .andExpect(jsonPath("$[0].availableSlots").value(2))

        val second = fx.order(store.slug, store.productId, phone = "912000111").andReturn().response.contentAsString
        assertThat(second).contains("-000002")
        // El comprador no tiene cuenta: no se creó ningún usuario adicional.
        assertThat(jdbc.queryForObject("select count(*) from users where phone = '+51987111222'", Int::class.java)).isZero()
        assertThat(jdbc.queryForObject("select count(*) from customers where phone = '+51987111222'", Int::class.java)).isEqualTo(1)
    }

    @Test
    fun `el mismo celular es un cliente por negocio y se reutiliza dentro del negocio`() {
        val storeA = fx.create(registerSeller())
        val storeB = fx.create(registerSeller())
        repeat(2) { fx.order(storeA.slug, storeA.productId, phone = "955444333").andExpect(status().isCreated) }
        fx.order(storeB.slug, storeB.productId, phone = "955444333").andExpect(status().isCreated)

        val perBusiness = jdbc.queryForList("select business_id from customers where phone = '+51955444333'")
        assertThat(perBusiness).hasSize(2)
        val consents = jdbc.queryForObject("select count(*) from customer_consents c join customers u on u.id = c.customer_id where u.phone = '+51955444333'", Int::class.java)
        assertThat(consents).isEqualTo(12) // 3 pedidos × (términos, datos, recordatorios, confirmaciones)
    }

    @Test
    fun `valida datos, consentimientos y metodo de pago`() {
        val store = fx.create(registerSeller())
        mockMvc.perform(
            post("/api/v1/public/stores/${store.slug}/orders").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("""{"productId":"${store.productId}","customerName":"","phone":"123","paymentMethod":"YAPE","acceptTerms":false,"acceptDataProcessing":false}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errors.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(4)))
        fx.order(store.slug, store.productId, method = "PLIN")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errors[0].field").value("paymentMethod"))
    }

    @Test
    fun `no permite comprar productos agotados, pausados o despublicados`() {
        val store = fx.create(registerSeller(), capacity = 1)
        fx.order(store.slug, store.productId, phone = "911000001").andExpect(status().isCreated)
        fx.order(store.slug, store.productId, phone = "911000002")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("PRODUCT_SOLD_OUT"))
        mockMvc.perform(get("/api/v1/public/stores/${store.slug}/products"))
            .andExpect(jsonPath("$[0].soldOut").value(true))
            .andExpect(jsonPath("$[0].purchasable").value(false))
        // El pedido rechazado no consumió número ni dejó datos a medias.
        assertThat(jdbc.queryForObject("select count(*) from orders o join businesses b on b.id = o.business_id where b.slug = ?", Int::class.java, store.slug)).isEqualTo(1)

        fx.publish(store.seller, store.productId, "DRAFT")
        fx.order(store.slug, store.productId, phone = "911000003").andExpect(status().isNotFound)
    }

    @Test
    fun `un negocio suspendido no expone catalogo ni acepta pedidos`() {
        val store = fx.create(registerSeller())
        jdbc.update("update businesses set status = 'SUSPENDED', suspended_at = now() where slug = ?", store.slug)
        mockMvc.perform(get("/api/v1/public/stores/${store.slug}"))
            .andExpect(jsonPath("$.acceptingOrders").value(false))
            .andExpect(jsonPath("$.paymentMethods.length()").value(0))
            .andExpect(jsonPath("$.description").doesNotExist())
        mockMvc.perform(get("/api/v1/public/stores/${store.slug}/products")).andExpect(jsonPath("$.length()").value(0))
        fx.order(store.slug, store.productId).andExpect(status().isConflict).andExpect(jsonPath("$.code").value("STORE_NOT_ACCEPTING_ORDERS"))
    }

    @Test
    fun `reintentar con la misma Idempotency-Key no duplica el pedido`() {
        val store = fx.create(registerSeller())
        val key = UUID.randomUUID()
        val first = fx.order(store.slug, store.productId, idempotencyKey = key).andReturn().response.contentAsString
        val second = fx.order(store.slug, store.productId, idempotencyKey = key).andReturn().response.contentAsString
        assertThat(fx.idFrom(first, "orderNumber")).isEqualTo(fx.idFrom(second, "orderNumber"))
        mockMvc.perform(get("/api/v1/public/orders/${fx.idFrom(second, "publicToken")}")).andExpect(status().isOk)
        assertThat(jdbc.queryForObject("select count(*) from orders where idempotency_key = ?", Int::class.java, key)).isEqualTo(1)
    }

    @Test
    fun `el enlace de WhatsApp usa el numero del negocio y el estado real`() {
        val store = fx.create(registerSeller())
        val token = fx.placeOrder(store)
        val url = fx.idFrom(mockMvc.perform(get("/api/v1/public/orders/$token")).andReturn().response.contentAsString, "whatsappUrl")
        assertThat(url).startsWith("https://wa.me/51912345678?text=").contains("Pendiente%20de%20pago", "Servicio%20Premium", "S%2F%2015.00")

        val noWhatsapp = fx.create(registerSeller(), whatsapp = null)
        mockMvc.perform(get("/api/v1/public/orders/${fx.placeOrder(noWhatsapp)}")).andExpect(jsonPath("$.whatsappUrl").doesNotExist())
    }

    @Test
    fun `un token de pedido invalido no revela nada`() {
        mockMvc.perform(get("/api/v1/public/orders/token-corto")).andExpect(status().isNotFound)
        mockMvc.perform(get("/api/v1/public/orders/" + "A".repeat(43))).andExpect(status().isNotFound)
    }
}
