package com.nuvyra.subscriptions

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
import java.time.Duration
import java.time.Instant

class SubscriptionIntegrationTest : IntegrationTest() {
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var expirationJob: SubscriptionExpirationJob
    lateinit var fx: StoreFixture

    @BeforeEach
    fun setUp() {
        fx = StoreFixture(mockMvc)
    }

    private fun subscriptionOf(orderId: String) =
        jdbc.queryForObject("select id::text from customer_subscriptions where order_id = ?::uuid", String::class.java, orderId)!!

    private fun activate(seller: jakarta.servlet.http.Cookie, id: String, startAt: Instant? = null) =
        mockMvc.perform(
            post("/api/v1/subscriptions/$id/activate").with(csrf()).cookie(seller).contentType(MediaType.APPLICATION_JSON)
                .content(startAt?.let { """{"startAt":"$it"}""" } ?: "{}"),
        )

    @Test
    fun `verificar el pago crea la suscripcion pendiente de activacion (pago no es entrega)`() {
        val store = fx.create(registerSeller())
        val orderId = fx.paidOrder(store)
        mockMvc.perform(get("/api/v1/orders/$orderId").cookie(store.seller)).andExpect(jsonPath("$.subscriptionStatus").value("Por activar"))
        mockMvc.perform(get("/api/v1/subscriptions/${subscriptionOf(orderId)}").cookie(store.seller))
            .andExpect(jsonPath("$.status").value("PENDING_ACTIVATION"))
            .andExpect(jsonPath("$.paidAt").exists())
            .andExpect(jsonPath("$.startAt").doesNotExist())
            .andExpect(jsonPath("$.expiresAt").doesNotExist())
            .andExpect(jsonPath("$.origin").value("ORDER"))
            .andExpect(jsonPath("$.contractedPrice").value(15.0))

        val oneTime = fx.createProduct(store.seller, """{"name":"Licencia","productType":"ONE_TIME","price":35,"capacityEnabled":false,"available":true}""")
        val oneTimeOrder = fx.paidOrder(store, phone = "911000003", productId = oneTime)
        assertThat(jdbc.queryForObject("select count(*) from customer_subscriptions where order_id = ?::uuid", Int::class.java, oneTimeOrder)).isZero()
    }

    @Test
    fun `activar registra inicio real y vencimiento segun la duracion`() {
        val store = fx.create(registerSeller())
        val id = subscriptionOf(fx.paidOrder(store))
        val start = Instant.now().minus(Duration.ofDays(2)).truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
        activate(store.seller, id, start)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.startAt").value(start.toString()))
            .andExpect(jsonPath("$.expiresAt").value(start.plus(Duration.ofDays(30)).toString()))
            .andExpect(jsonPath("$.daysRemaining").value(28))
        activate(store.seller, id).andExpect(status().isConflict).andExpect(jsonPath("$.code").value("SUBSCRIPTION_NOT_PENDING"))

        val other = subscriptionOf(fx.paidOrder(store, phone = "911000004"))
        activate(store.seller, other, Instant.now().plus(Duration.ofDays(90))).andExpect(status().isBadRequest)
    }

    @Test
    fun `al vencer el cupo sigue ocupado hasta confirmar que se retiro el acceso`() {
        val store = fx.create(registerSeller(), capacity = 1)
        val id = subscriptionOf(fx.paidOrder(store))
        activate(store.seller, id)
        jdbc.update("update customer_subscriptions set start_at = now() - interval '31 days', expires_at = now() - interval '1 day' where id = ?::uuid", id)

        assertThat(expirationJob.expireOverdue()).isGreaterThanOrEqualTo(1)
        assertThat(expirationJob.expireOverdue()).isZero()
        mockMvc.perform(get("/api/v1/subscriptions/$id").cookie(store.seller))
            .andExpect(jsonPath("$.status").value("EXPIRED"))
            .andExpect(jsonPath("$.capacityStatus").value("RELEASE_PENDING"))
        mockMvc.perform(get("/api/v1/public/stores/${store.slug}/products")).andExpect(jsonPath("$[0].soldOut").value(true))

        mockMvc.perform(post("/api/v1/subscriptions/$id/release-capacity").with(csrf()).cookie(store.seller))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("ENDED"))
            .andExpect(jsonPath("$.capacityStatus").value("RELEASED"))
        mockMvc.perform(get("/api/v1/public/stores/${store.slug}/products")).andExpect(jsonPath("$[0].availableSlots").value(1))
    }

    @Test
    fun `no se libera el cupo de una suscripcion vigente y cancelar lo deja pendiente`() {
        val store = fx.create(registerSeller(), capacity = 2)
        val id = subscriptionOf(fx.paidOrder(store))
        activate(store.seller, id)
        mockMvc.perform(post("/api/v1/subscriptions/$id/release-capacity").with(csrf()).cookie(store.seller))
            .andExpect(status().isConflict).andExpect(jsonPath("$.code").value("ACCESS_STILL_VALID"))
        mockMvc.perform(post("/api/v1/subscriptions/$id/cancel").with(csrf()).cookie(store.seller).contentType(MediaType.APPLICATION_JSON).content("""{"reason":"Solicitud del cliente"}"""))
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.capacityStatus").value("RELEASE_PENDING"))
    }

    @Test
    fun `filtra activas, por vencer y por activar, sin cruzar negocios`() {
        val store = fx.create(registerSeller())
        val soon = subscriptionOf(fx.paidOrder(store, phone = "911000001"))
        val later = subscriptionOf(fx.paidOrder(store, phone = "911000002"))
        fx.paidOrder(store, phone = "911000003")
        activate(store.seller, soon, Instant.now().minus(Duration.ofDays(28)))
        activate(store.seller, later)

        mockMvc.perform(get("/api/v1/subscriptions").cookie(store.seller).param("filter", "ACTIVE")).andExpect(jsonPath("$.totalItems").value(2))
        mockMvc.perform(get("/api/v1/subscriptions/expiring").cookie(store.seller).param("days", "3"))
            .andExpect(jsonPath("$.totalItems").value(1))
            .andExpect(jsonPath("$.items[0].id").value(soon))
        mockMvc.perform(get("/api/v1/subscriptions").cookie(store.seller).param("filter", "PENDING_ACTIVATION")).andExpect(jsonPath("$.totalItems").value(1))
        mockMvc.perform(get("/api/v1/subscriptions").cookie(store.seller).param("query", "911000002")).andExpect(jsonPath("$.items[0].id").value(later))

        val sellerB = registerSeller()
        mockMvc.perform(get("/api/v1/subscriptions").cookie(sellerB)).andExpect(jsonPath("$.totalItems").value(0))
        mockMvc.perform(get("/api/v1/subscriptions/$soon").cookie(sellerB)).andExpect(status().isNotFound)
        activate(sellerB, later).andExpect(status().isNotFound)
    }
}
