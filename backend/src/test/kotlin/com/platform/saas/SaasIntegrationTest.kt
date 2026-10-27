package com.platform.saas

import com.platform.support.IntegrationTest
import com.platform.support.StoreFixture
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.UUID

class SaasIntegrationTest : IntegrationTest() {
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var job: SaasBillingJob
    lateinit var fx: StoreFixture
    lateinit var admin: Cookie

    @BeforeEach
    fun setUp() {
        fx = StoreFixture(mockMvc)
        admin = loginSuperAdmin()
        mockMvc.perform(
            put("/api/v1/admin/settings/payment-methods/yape").with(csrf()).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content("""{"holderName":"Plataforma SAC","phone":"955111222","enabled":true}"""),
        ).andExpect(status().isOk)
    }

    private fun createPlan(
        price: String = "49.90",
        period: String = "MONTHLY",
        productLimit: Int? = null,
        customerLimit: Int? = null,
        reports: Boolean = true,
        branding: Boolean = true,
    ): String {
        val body = mockMvc.perform(
            post("/api/v1/admin/plans").with(csrf()).cookie(admin).contentType(MediaType.APPLICATION_JSON).content(
                """{"name":"Plan ${UUID.randomUUID().toString().take(6)}","description":"Para empezar","price":$price,"billingPeriod":"$period",
                   "productLimit":${productLimit ?: "null"},"customerLimit":${customerLimit ?: "null"},"adminUserLimit":1,
                   "featureReports":$reports,"featureReminders":true,"featureBranding":$branding,"active":true,"sortOrder":1}""",
            ),
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        return fx.idFrom(body)
    }

    private fun checkout(seller: Cookie, planId: String): ResultActions =
        mockMvc.perform(post("/api/v1/billing/checkout").with(csrf()).cookie(seller).contentType(MediaType.APPLICATION_JSON).content("""{"planId":"$planId"}"""))

    private fun openInvoiceId(seller: Cookie): String {
        val body = mockMvc.perform(get("/api/v1/billing").cookie(seller)).andReturn().response.contentAsString
        return Regex("\"openInvoice\":\\{\"id\":\"([^\"]+)\"").find(body)!!.groupValues[1]
    }

    private fun report(seller: Cookie, invoiceId: String, reference: String = "OP" + (System.nanoTime() % 100_000_000)): ResultActions =
        mockMvc.perform(
            multipart("/api/v1/billing/invoices/$invoiceId/payments").file(MockMultipartFile("receipt", "pago.png", "image/png", pngBytes()))
                .param("method", "YAPE").param("transactionReference", reference).with(csrf()).cookie(seller),
        )

    private fun pendingPaymentId(businessId: UUID) = jdbc.queryForObject(
        "select id from saas_payments where business_id = ? and status = 'PENDING_VERIFICATION'", UUID::class.java, businessId,
    )!!

    private fun createProduct(seller: Cookie, name: String = "Servicio") = mockMvc.perform(
        post("/api/v1/products").with(csrf()).cookie(seller).contentType(MediaType.APPLICATION_JSON).content(
            """{"name":"$name","productType":"SUBSCRIPTION","price":15,"durationDays":30,"capacityEnabled":false,"available":true}""",
        ),
    )

    private fun revenueTotal(): BigDecimal = BigDecimal(
        Regex("\"revenueTotal\":([0-9.]+)").find(mockMvc.perform(get("/api/v1/admin/overview").cookie(admin)).andReturn().response.contentAsString)!!.groupValues[1],
    )

    @Test
    fun `sin plan vigente el panel queda en solo lectura y la tienda cerrada`() {
        val email = uniqueEmail()
        val seller = registerSeller(email, withPlan = false)

        mockMvc.perform(get("/api/v1/auth/me").cookie(seller))
            .andExpect(jsonPath("$.business.access.active").value(false))
            .andExpect(jsonPath("$.business.access.block").value("NO_PLAN"))
        createProduct(seller).andExpect(status().isForbidden).andExpect(jsonPath("$.code").value("PLAN_REQUIRED"))
        mockMvc.perform(get("/api/v1/products").cookie(seller)).andExpect(status().isOk)
        mockMvc.perform(get("/api/v1/billing").cookie(seller)).andExpect(status().isOk)
            .andExpect(jsonPath("$.access.active").value(false))
            .andExpect(jsonPath("$.paymentOptions[0].holderName").value("Plataforma SAC"))

        val slug = fx.idFrom(mockMvc.perform(get("/api/v1/business/me").cookie(seller)).andReturn().response.contentAsString, "slug")
        mockMvc.perform(get("/api/v1/public/stores/$slug"))
            .andExpect(jsonPath("$.acceptingOrders").value(false))
            .andExpect(jsonPath("$.paymentMethods").isEmpty)
    }

    @Test
    fun `reportar el pago no activa el plan, la verificacion del superadministrador si`() {
        val planId = createPlan(price = "49.90")
        val email = uniqueEmail()
        val seller = registerSeller(email, withPlan = false)
        val businessId = businessIdOf(email)
        val before = revenueTotal()

        checkout(seller, planId).andExpect(status().isOk)
            .andExpect(jsonPath("$.openInvoice.kind").value("NEW"))
            .andExpect(jsonPath("$.openInvoice.amount").value(49.90))
            .andExpect(jsonPath("$.access.block").value("PENDING_PAYMENT"))
        // Elegir el mismo plan otra vez no genera otro cobro.
        val invoiceId = openInvoiceId(seller)
        checkout(seller, planId).andExpect(jsonPath("$.openInvoice.id").value(invoiceId))

        report(seller, invoiceId, "OP-777").andExpect(status().isOk)
            .andExpect(jsonPath("$.openInvoice.payments[0].status").value("PENDING_VERIFICATION"))
        report(seller, invoiceId, "OP-778").andExpect(status().isConflict).andExpect(jsonPath("$.code").value("PAYMENT_ALREADY_REPORTED"))
        createProduct(seller).andExpect(status().isForbidden)

        val paymentId = pendingPaymentId(businessId)
        mockMvc.perform(post("/api/v1/admin/saas-payments/$paymentId/verify").with(csrf()).cookie(admin)).andExpect(status().isOk)
        mockMvc.perform(post("/api/v1/admin/saas-payments/$paymentId/verify").with(csrf()).cookie(admin))
            .andExpect(status().isConflict).andExpect(jsonPath("$.code").value("PAYMENT_ALREADY_PROCESSED"))

        mockMvc.perform(get("/api/v1/billing").cookie(seller))
            .andExpect(jsonPath("$.access.active").value(true))
            .andExpect(jsonPath("$.access.subscriptionStatus").value("ACTIVE"))
            .andExpect(jsonPath("$.openInvoice").doesNotExist())
            .andExpect(jsonPath("$.history[0].status").value("PAID"))
        createProduct(seller).andExpect(status().isCreated)
        val expires = jdbc.queryForObject("select expires_at from saas_subscriptions where business_id = ?", java.sql.Timestamp::class.java, businessId)!!.toInstant()
        assertThat(Duration.between(Instant.now(), expires).toDays()).isBetween(27L, 31L)
        assertThat(revenueTotal() - before).isEqualByComparingTo("49.90")
        // El código de operación no puede reutilizarse en otro cobro.
        val other = registerSeller(withPlan = false)
        checkout(other, planId)
        report(other, openInvoiceId(other), "OP-777").andExpect(status().isConflict).andExpect(jsonPath("$.code").value("DUPLICATE_REFERENCE"))
    }

    @Test
    fun `un pago rechazado permite reportar de nuevo y no suma ingresos`() {
        val planId = createPlan()
        val email = uniqueEmail()
        val seller = registerSeller(email, withPlan = false)
        checkout(seller, planId)
        val invoiceId = openInvoiceId(seller)
        report(seller, invoiceId).andExpect(status().isOk)
        val before = revenueTotal()
        mockMvc.perform(
            post("/api/v1/admin/saas-payments/${pendingPaymentId(businessIdOf(email))}/reject").with(csrf()).cookie(admin)
                .contentType(MediaType.APPLICATION_JSON).content("""{"reason":"No encontramos el pago en Yape"}"""),
        ).andExpect(status().isOk)
        assertThat(revenueTotal()).isEqualByComparingTo(before)
        mockMvc.perform(get("/api/v1/billing").cookie(seller))
            .andExpect(jsonPath("$.access.active").value(false))
            .andExpect(jsonPath("$.openInvoice.payments[0].status").value("REJECTED"))
            .andExpect(jsonPath("$.openInvoice.payments[0].rejectionReason").value("No encontramos el pago en Yape"))
        report(seller, invoiceId).andExpect(status().isOk)
    }

    @Test
    fun `un plan gratuito se activa sin pago y el registro puede elegir plan`() {
        val free = createPlan(price = "0")
        val seller = registerSeller(withPlan = false)
        checkout(seller, free).andExpect(jsonPath("$.access.active").value(true)).andExpect(jsonPath("$.history[0].amount").value(0))

        val paid = createPlan(price = "30")
        val email = uniqueEmail()
        val json = registerJson(email = email).replace("\"acceptPrivacy\":true", "\"acceptPrivacy\":true,\"planId\":\"$paid\"")
        mockMvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.business.access.block").value("PENDING_PAYMENT"))
        assertThat(jdbc.queryForObject("select count(*) from saas_invoices where business_id = ? and status = 'OPEN'", Int::class.java, businessIdOf(email))).isEqualTo(1)
    }

    @Test
    fun `cambiar de plan con un pago en revision no se permite y anular deja sin plan`() {
        val a = createPlan()
        val b = createPlan(price = "99")
        val seller = registerSeller(withPlan = false)
        checkout(seller, a)
        checkout(seller, b).andExpect(jsonPath("$.openInvoice.planId").value(b))
        val invoiceId = openInvoiceId(seller)
        report(seller, invoiceId)
        checkout(seller, a).andExpect(status().isConflict).andExpect(jsonPath("$.code").value("PAYMENT_UNDER_REVIEW"))
        mockMvc.perform(post("/api/v1/billing/invoices/$invoiceId/cancel").with(csrf()).cookie(seller)).andExpect(status().isConflict)

        val other = registerSeller(withPlan = false)
        checkout(other, a)
        mockMvc.perform(post("/api/v1/billing/invoices/${openInvoiceId(other)}/cancel").with(csrf()).cookie(other))
            .andExpect(status().isOk).andExpect(jsonPath("$.access.block").value("CANCELLED")).andExpect(jsonPath("$.openInvoice").doesNotExist())
    }

    @Test
    fun `limite de productos del plan y los archivados no cuentan`() {
        val email = uniqueEmail()
        val seller = registerSeller(email, withPlan = false)
        grantPlan(businessIdOf(email), UUID.fromString(createPlan(productLimit = 1)))
        val first = fx.idFrom(createProduct(seller, "Uno").andExpect(status().isCreated).andReturn().response.contentAsString)
        createProduct(seller, "Dos").andExpect(status().isConflict).andExpect(jsonPath("$.code").value("PLAN_LIMIT_REACHED"))
        fx.publish(seller, first, "ARCHIVED")
        val second = fx.idFrom(createProduct(seller, "Dos").andExpect(status().isCreated).andReturn().response.contentAsString)
        assertThat(second).isNotEqualTo(first)
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/products/$first/status").with(csrf()).cookie(seller)
                .contentType(MediaType.APPLICATION_JSON).content("""{"publicationStatus":"DRAFT"}"""),
        ).andExpect(status().isConflict).andExpect(jsonPath("$.code").value("PLAN_LIMIT_REACHED"))
    }

    @Test
    fun `limite de clientes cierra pedidos de compradores nuevos sin revelar el plan`() {
        val email = uniqueEmail()
        val seller = registerSeller(email, withPlan = false)
        grantPlan(businessIdOf(email), UUID.fromString(createPlan(customerLimit = 1)))
        val store = fx.create(seller)
        fx.order(store.slug, store.productId, phone = "911000001").andExpect(status().isCreated)
        fx.order(store.slug, store.productId, phone = "911000002").andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("STORE_NOT_ACCEPTING_ORDERS"))
        // Quien ya es cliente puede volver a comprar.
        fx.order(store.slug, store.productId, phone = "911000001").andExpect(status().isCreated)
        mockMvc.perform(
            post("/api/v1/customers").with(csrf()).cookie(seller).contentType(MediaType.APPLICATION_JSON)
                .content("""{"fullName":"Cliente Antiguo","phone":"911000003"}"""),
        ).andExpect(status().isConflict).andExpect(jsonPath("$.code").value("PLAN_LIMIT_REACHED"))
    }

    @Test
    fun `funciones fuera del plan se bloquean en el servidor`() {
        val email = uniqueEmail()
        val seller = registerSeller(email, withPlan = false)
        grantPlan(businessIdOf(email), UUID.fromString(createPlan(reports = false, branding = false)))
        mockMvc.perform(get("/api/v1/reports/sales").cookie(seller)).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("FEATURE_NOT_IN_PLAN"))
        mockMvc.perform(get("/api/v1/reports/dashboard").cookie(seller)).andExpect(status().isOk)
        mockMvc.perform(
            put("/api/v1/business/me").with(csrf()).cookie(seller).contentType(MediaType.APPLICATION_JSON)
                .content("""{"businessName":"Tienda","timeZone":"America/Lima","primaryColor":"#AA3355"}"""),
        ).andExpect(status().isForbidden).andExpect(jsonPath("$.code").value("FEATURE_NOT_IN_PLAN"))
        mockMvc.perform(
            put("/api/v1/business/me").with(csrf()).cookie(seller).contentType(MediaType.APPLICATION_JSON)
                .content("""{"businessName":"Tienda sin color","timeZone":"America/Lima"}"""),
        ).andExpect(status().isOk)
    }

    @Test
    fun `suspender cierra la tienda y bloquea el panel, reactivar lo restablece`() {
        val email = uniqueEmail()
        val seller = registerSeller(email)
        val store = fx.create(seller)
        val businessId = businessIdOf(email)
        mockMvc.perform(
            post("/api/v1/admin/businesses/$businessId/suspend").with(csrf()).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content("""{"reason":"Venta de servicios no autorizados"}"""),
        ).andExpect(status().isOk).andExpect(jsonPath("$.summary.status").value("SUSPENDED"))

        fx.order(store.slug, store.productId).andExpect(status().isConflict)
        mockMvc.perform(get("/api/v1/public/stores/${store.slug}/products")).andExpect(jsonPath("$").isEmpty)
        createProduct(seller).andExpect(status().isForbidden).andExpect(jsonPath("$.code").value("BUSINESS_SUSPENDED"))
        mockMvc.perform(get("/api/v1/billing").cookie(seller))
            .andExpect(jsonPath("$.suspensionReason").value("Venta de servicios no autorizados"))

        mockMvc.perform(post("/api/v1/admin/businesses/$businessId/reactivate").with(csrf()).cookie(admin)).andExpect(status().isOk)
        fx.order(store.slug, store.productId).andExpect(status().isCreated)
    }

    @Test
    fun `sin plan el vendedor aun puede verificar pagos de compradores que ya pagaron`() {
        val email = uniqueEmail()
        val seller = registerSeller(email)
        val store = fx.create(seller)
        fx.report(fx.placeOrder(store)).andExpect(status().isOk)
        jdbc.update("update saas_subscriptions set status = 'EXPIRED' where business_id = ?", businessIdOf(email))

        val (_, paymentId) = fx.latestPaymentId(seller)
        mockMvc.perform(post("/api/v1/payments/$paymentId/verify").with(csrf()).cookie(seller)).andExpect(status().isOk)
        createProduct(seller).andExpect(status().isForbidden)
    }

    @Test
    fun `vencimiento pasa por gracia y luego deja sin vigencia, renovar continua desde el vencimiento`() {
        val planId = createPlan(price = "20")
        val email = uniqueEmail()
        val seller = registerSeller(email, withPlan = false)
        val businessId = businessIdOf(email)
        grantPlan(businessId, UUID.fromString(planId))
        mockMvc.perform(put("/api/v1/admin/settings").with(csrf()).cookie(admin).contentType(MediaType.APPLICATION_JSON).content("""{"saasGraceDays":5}"""))
            .andExpect(status().isOk)
        jdbc.update("update saas_subscriptions set expires_at = now() - interval '1 day' where business_id = ?", businessId)

        job.run()
        job.run()
        val (status, grace) = jdbc.queryForMap("select status, grace_until from saas_subscriptions where business_id = ?", businessId)
            .let { it["status"] to it["grace_until"] }
        assertThat(status).isEqualTo("PAST_DUE")
        assertThat(grace).isNotNull
        mockMvc.perform(get("/api/v1/auth/me").cookie(seller)).andExpect(jsonPath("$.business.access.active").value(true))

        // Renovar en gracia continúa desde el vencimiento anterior.
        val oldExpiry = jdbc.queryForObject("select expires_at from saas_subscriptions where business_id = ?", java.sql.Timestamp::class.java, businessId)!!.toInstant()
        checkout(seller, planId).andExpect(jsonPath("$.openInvoice.kind").value("RENEWAL"))
        report(seller, openInvoiceId(seller))
        mockMvc.perform(post("/api/v1/admin/saas-payments/${pendingPaymentId(businessId)}/verify").with(csrf()).cookie(admin)).andExpect(status().isOk)
        val period = jdbc.queryForMap("select period_start, period_end from saas_invoices where business_id = ? and status = 'PAID'", businessId)
        assertThat((period["period_start"] as java.sql.Timestamp).toInstant()).isEqualTo(oldExpiry)
        assertThat(jdbc.queryForObject("select status from saas_subscriptions where business_id = ?", String::class.java, businessId)).isEqualTo("ACTIVE")

        // Sin pago al terminar la gracia: EXPIRED y panel en solo lectura.
        jdbc.update("update saas_subscriptions set status = 'PAST_DUE', start_at = now() - interval '40 day', expires_at = now() - interval '10 day', grace_until = now() - interval '1 minute' where business_id = ?", businessId)
        job.run()
        assertThat(jdbc.queryForObject("select status from saas_subscriptions where business_id = ?", String::class.java, businessId)).isEqualTo("EXPIRED")
        createProduct(seller).andExpect(status().isForbidden).andExpect(jsonPath("$.code").value("PLAN_REQUIRED"))
    }

    @Test
    fun `solo el superadministrador accede al panel y la auditoria no expone datos de compradores`() {
        val email = uniqueEmail()
        val seller = registerSeller(email)
        val store = fx.create(seller)
        fx.placeOrder(store)
        mockMvc.perform(get("/api/v1/admin/overview").cookie(seller)).andExpect(status().isForbidden)
        mockMvc.perform(get("/api/v1/admin/plans").cookie(seller)).andExpect(status().isForbidden)
        mockMvc.perform(get("/api/v1/products").cookie(admin)).andExpect(status().isForbidden)

        val businessId = businessIdOf(email)
        mockMvc.perform(get("/api/v1/admin/businesses/$businessId").cookie(admin)).andExpect(status().isOk)
            .andExpect(jsonPath("$.usage.customers").value(1))
            .andExpect(jsonPath("$.summary.ownerEmail").value(email))
        mockMvc.perform(get("/api/v1/admin/audit-logs").param("businessId", businessId.toString()).param("action", "ORDER_CREATED").cookie(admin))
            .andExpect(jsonPath("$.items[0].action").value("ORDER_CREATED"))
            .andExpect(jsonPath("$.items[0].metadata").doesNotExist())
            .andExpect(jsonPath("$.items[0].ipAddress").doesNotExist())
        mockMvc.perform(get("/api/v1/admin/businesses").param("q", email).cookie(admin))
            .andExpect(jsonPath("$.totalItems").value(1)).andExpect(jsonPath("$.items[0].subscriptionStatus").value("ACTIVE"))
    }

    @Test
    fun `planes publicos solo muestran los activos`() {
        val planId = createPlan()
        mockMvc.perform(
            put("/api/v1/admin/plans/$planId").with(csrf()).cookie(admin).contentType(MediaType.APPLICATION_JSON).content(
                """{"name":"Retirado $planId","price":10,"billingPeriod":"YEARLY","adminUserLimit":1,"featureReports":true,
                   "featureReminders":true,"featureBranding":true,"active":false,"sortOrder":1}""",
            ),
        ).andExpect(status().isOk).andExpect(jsonPath("$.active").value(false))
        val body = mockMvc.perform(get("/api/v1/public/saas-plans")).andExpect(status().isOk).andReturn().response.contentAsString
        assertThat(body).doesNotContain(planId)
        val seller = registerSeller(withPlan = false)
        checkout(seller, planId).andExpect(status().isConflict).andExpect(jsonPath("$.code").value("PLAN_UNAVAILABLE"))
    }
}
