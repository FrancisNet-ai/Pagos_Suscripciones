package com.platform.customers

import com.platform.support.IntegrationTest
import com.platform.support.StoreFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate

class CustomerIntegrationTest : IntegrationTest() {
    @Autowired lateinit var jdbc: JdbcTemplate
    lateinit var fx: StoreFixture

    @BeforeEach
    fun setUp() {
        fx = StoreFixture(mockMvc)
    }

    private fun subscriptionJson(productId: String, start: LocalDate = LocalDate.now().minusDays(10), days: Int = 30, status: String = "ACTIVE") =
        """{"productId":"$productId","startDate":"$start","durationDays":$days,"contractedPrice":12.50,"status":"$status","notes":"Cliente desde 2025"}"""

    private fun createCustomer(seller: jakarta.servlet.http.Cookie, name: String, phone: String, subscription: String? = null) =
        mockMvc.perform(
            post("/api/v1/customers").with(csrf()).cookie(seller).contentType(MediaType.APPLICATION_JSON)
                .content("""{"fullName":"$name","phone":"$phone","notes":null,"subscription":${subscription ?: "null"}}"""),
        )

    @Test
    fun `registra un cliente antiguo con su suscripcion sin generar venta ni ingreso`() {
        val store = fx.create(registerSeller())
        val start = LocalDate.now().minusDays(10)
        createCustomer(store.seller, "María López", "966 555 444", subscriptionJson(store.productId, start))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.source").value("MANUAL"))
            .andExpect(jsonPath("$.phone").value("+51966555444"))
            .andExpect(jsonPath("$.subscriptions[0].origin").value("MANUAL"))
            .andExpect(jsonPath("$.subscriptions[0].status").value("ACTIVE"))
            .andExpect(jsonPath("$.subscriptions[0].daysRemaining").value(org.hamcrest.Matchers.either(org.hamcrest.Matchers.`is`(20)).or(org.hamcrest.Matchers.`is`(21))))
            .andExpect(jsonPath("$.subscriptions[0].contractedPrice").value(12.5))
            .andExpect(jsonPath("$.orders.length()").value(0))

        assertThat(jdbc.queryForObject("select count(*) from orders o join customers c on c.id = o.customer_id where c.phone = '+51966555444'", Int::class.java)).isZero()
        mockMvc.perform(get("/api/v1/reports/dashboard").cookie(store.seller))
            .andExpect(jsonPath("$.confirmedMonth.amount").value(0))
            .andExpect(jsonPath("$.subscriptions.active").value(1))
    }

    @Test
    fun `el celular es unico por negocio pero puede repetirse entre negocios`() {
        val sellerA = registerSeller()
        val sellerB = registerSeller()
        val created = createCustomer(sellerA, "Ana", "955111222").andReturn().response.contentAsString
        createCustomer(sellerA, "Ana duplicada", "+51955111222")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("CUSTOMER_EXISTS"))
            .andExpect(jsonPath("$.customerId").value(fx.idFrom(created)))
        createCustomer(sellerB, "Ana en otra tienda", "955111222").andExpect(status().isCreated)
    }

    @Test
    fun `valida estado contra fechas y evita duplicar la misma suscripcion`() {
        val store = fx.create(registerSeller())
        createCustomer(store.seller, "Luis", "944000001", subscriptionJson(store.productId, LocalDate.now().minusDays(60), 30, "ACTIVE"))
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.errors[0].field").value("status"))
        createCustomer(store.seller, "Luis", "944000001", subscriptionJson(store.productId, LocalDate.now().minusDays(5), 30, "EXPIRED"))
            .andExpect(status().isBadRequest)

        val id = fx.idFrom(createCustomer(store.seller, "Luis", "944000001").andReturn().response.contentAsString)
        val body = subscriptionJson(store.productId, LocalDate.now().minusDays(60), 30, "EXPIRED")
        mockMvc.perform(post("/api/v1/customers/$id/subscriptions").with(csrf()).cookie(store.seller).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk).andExpect(jsonPath("$.subscriptions[0].status").value("EXPIRED"))
        mockMvc.perform(post("/api/v1/customers/$id/subscriptions").with(csrf()).cookie(store.seller).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isConflict).andExpect(jsonPath("$.code").value("DUPLICATE_SUBSCRIPTION"))
    }

    @Test
    fun `un cliente antiguo activo ocupa cupo y respeta la capacidad`() {
        val store = fx.create(registerSeller(), capacity = 1)
        createCustomer(store.seller, "Primero", "933000001", subscriptionJson(store.productId)).andExpect(status().isCreated)
        mockMvc.perform(get("/api/v1/public/stores/${store.slug}/products")).andExpect(jsonPath("$[0].soldOut").value(true))
        createCustomer(store.seller, "Segundo", "933000002", subscriptionJson(store.productId))
            .andExpect(status().isConflict).andExpect(jsonPath("$.code").value("PRODUCT_SOLD_OUT"))
        // Toda la operación se revierte: no queda el cliente a medias.
        assertThat(jdbc.queryForObject("select count(*) from customers where phone = '+51933000002'", Int::class.java)).isZero()
    }

    @Test
    fun `busca y filtra clientes por situacion sin mezclar negocios`() {
        val store = fx.create(registerSeller())
        createCustomer(store.seller, "Activa Pérez", "922000001", subscriptionJson(store.productId, LocalDate.now().minusDays(5)))
        createCustomer(store.seller, "Por Vencer Díaz", "922000002", subscriptionJson(store.productId, LocalDate.now().minusDays(27)))
        createCustomer(store.seller, "Vencida Ruiz", "922000003", subscriptionJson(store.productId, LocalDate.now().minusDays(90), 30, "EXPIRED"))
        createCustomer(store.seller, "Sin Suscripción", "922000004")

        mockMvc.perform(get("/api/v1/customers").cookie(store.seller)).andExpect(jsonPath("$.totalItems").value(4))
        mockMvc.perform(get("/api/v1/customers").cookie(store.seller).param("filter", "ACTIVE")).andExpect(jsonPath("$.totalItems").value(2))
        mockMvc.perform(get("/api/v1/customers").cookie(store.seller).param("filter", "EXPIRING"))
            .andExpect(jsonPath("$.totalItems").value(1)).andExpect(jsonPath("$.items[0].fullName").value("Por Vencer Díaz"))
        mockMvc.perform(get("/api/v1/customers").cookie(store.seller).param("filter", "EXPIRED"))
            .andExpect(jsonPath("$.items[0].fullName").value("Vencida Ruiz"))
        mockMvc.perform(get("/api/v1/customers").cookie(store.seller).param("query", "922000004"))
            .andExpect(jsonPath("$.items[0].fullName").value("Sin Suscripción"))
        mockMvc.perform(get("/api/v1/customers").cookie(store.seller).param("query", "ruiz"))
            .andExpect(jsonPath("$.totalItems").value(1))

        val customerId = fx.idFrom(mockMvc.perform(get("/api/v1/customers").cookie(store.seller).param("query", "Activa")).andReturn().response.contentAsString)
        val sellerB = registerSeller()
        mockMvc.perform(get("/api/v1/customers").cookie(sellerB)).andExpect(jsonPath("$.totalItems").value(0))
        mockMvc.perform(get("/api/v1/customers/$customerId").cookie(sellerB)).andExpect(status().isNotFound)
        mockMvc.perform(put("/api/v1/customers/$customerId").with(csrf()).cookie(sellerB).contentType(MediaType.APPLICATION_JSON).content("""{"fullName":"Hackeado","notes":null}"""))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `el detalle del cliente muestra sus compras en la tienda`() {
        val store = fx.create(registerSeller())
        fx.paidOrder(store, phone = "977000001")
        fx.paidOrder(store, phone = "977000001")
        val id = jdbc.queryForObject("select id::text from customers where phone = '+51977000001' and business_id = (select business_id from products where id = ?::uuid)", String::class.java, store.productId)!!
        mockMvc.perform(get("/api/v1/customers/$id").cookie(store.seller))
            .andExpect(jsonPath("$.source").value("STOREFRONT"))
            .andExpect(jsonPath("$.paidOrders").value(2))
            .andExpect(jsonPath("$.orders.length()").value(2))
            .andExpect(jsonPath("$.subscriptions.length()").value(2))
    }
}
