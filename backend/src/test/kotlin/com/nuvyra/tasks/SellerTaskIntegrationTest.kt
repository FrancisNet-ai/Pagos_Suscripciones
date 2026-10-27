package com.nuvyra.tasks

import com.nuvyra.renewals.RenewalJob
import com.nuvyra.payments.PaymentVerificationService
import com.nuvyra.subscriptions.SubscriptionExpirationJob
import com.nuvyra.support.IntegrationTest
import com.nuvyra.support.Store
import com.nuvyra.support.StoreFixture
import jakarta.servlet.http.Cookie
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
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.time.Instant
import java.util.UUID

class SellerTaskIntegrationTest : IntegrationTest() {
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var renewalJob: RenewalJob
    @Autowired lateinit var expirationJob: SubscriptionExpirationJob
    @Autowired lateinit var verification: PaymentVerificationService
    @Autowired lateinit var transactions: PlatformTransactionManager
    lateinit var fx: StoreFixture

    @BeforeEach
    fun setUp() {
        fx = StoreFixture(mockMvc)
    }

    private fun tasks(cookie: Cookie, view: String = "ALL"): JsonNode =
        objectMapper.readTree(mockMvc.perform(get("/api/v1/tasks").param("view", view).cookie(cookie)).andExpect(status().isOk).andReturn().response.contentAsString)

    private fun types(cookie: Cookie): List<String> = tasks(cookie)["items"].values().map { it["type"].asString() }

    private fun taskOf(cookie: Cookie, type: String) = tasks(cookie)["items"].values().first { it["type"].asString() == type }

    private fun history(cookie: Cookie, page: Int = 0, size: Int = 20): JsonNode = objectMapper.readTree(
        mockMvc.perform(get("/api/v1/tasks/history").cookie(cookie).param("page", page.toString()).param("size", size.toString()))
            .andExpect(status().isOk).andReturn().response.contentAsString,
    )

    private fun assign(cookie: Cookie, taskId: String, userId: UUID?) = mockMvc.perform(
        put("/api/v1/tasks/$taskId/assignee").with(csrf()).cookie(cookie).contentType(MediaType.APPLICATION_JSON)
            .content("""{"userId":${userId?.let { "\"$it\"" } ?: "null"}}"""),
    )

    private fun userIdOf(email: String): UUID = jdbc.queryForObject("select id from users where email = ?", UUID::class.java, email)!!

    /** Colaborador con las secciones indicadas (el plan de prueba se amplía a 5 usuarios). */
    private fun staffOf(owner: Cookie, ownerEmail: String, permissions: String): Pair<String, Cookie> {
        jdbc.update(
            "insert into saas_plans (name, price, billing_period, admin_user_limit) values ('Plan pendientes', 10, 'MONTHLY', 5) on conflict (name) do nothing",
        )
        grantPlan(businessIdOf(ownerEmail), jdbc.queryForObject("select id from saas_plans where name = 'Plan pendientes'", UUID::class.java)!!)
        val email = uniqueEmail()
        val body = mockMvc.perform(
            post("/api/v1/business/me/staff/invitations").with(csrf()).cookie(owner).contentType(MediaType.APPLICATION_JSON)
                .content("""{"fullName":"Luis Apoyo","email":"$email","permissions":$permissions}"""),
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        val token = objectMapper.readTree(body)["link"].asString().substringAfterLast('/')
        val accepted = mockMvc.perform(
            post("/api/v1/public/invitations/$token/accept").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("""{"fullName":"Luis Apoyo","phone":"976543210","password":"clave-colaborador-1","acceptTerms":true,"acceptPrivacy":true}"""),
        ).andExpect(status().isOk).andReturn()
        return email to accepted.response.getCookie("sid")!!
    }

    /** Pedido con el pago reportado (sin verificar). */
    private fun reportedOrder(store: Store, phone: String) {
        fx.report(fx.placeOrder(store, phone), reference = "R" + (System.nanoTime() % 100_000_000)).andExpect(status().isOk)
    }

    @Test
    fun `las tareas se abren y se resuelven con el estado, sin duplicarse`() {
        val email = uniqueEmail()
        val store = fx.create(registerSeller(email = email))
        reportedOrder(store, "911100001")

        val verify = taskOf(store.seller, "VERIFY_PAYMENT")
        assertThat(verify["customerName"].asString()).isEqualTo("Carlos Ramírez")
        assertThat(verify["orderNumber"].asString()).isNotBlank()
        assertThat(verify["amount"].decimalValue()).isEqualByComparingTo("15.00")
        assertThat(verify["overdue"].asBoolean()).isFalse()
        // Leer otra vez no duplica nada.
        tasks(store.seller)
        assertThat(jdbc.queryForObject("select count(*) from seller_tasks where business_id = ? and status = 'OPEN'", Int::class.java, businessIdOf(email))).isEqualTo(1)

        // Verificar el pago (en su pantalla) resuelve la tarea y abre la activación del servicio.
        val (_, paymentId) = fx.latestPaymentId(store.seller)
        mockMvc.perform(post("/api/v1/payments/$paymentId/verify").with(csrf()).cookie(store.seller)).andExpect(status().isOk)
        assertThat(types(store.seller)).containsExactly("ACTIVATE_SERVICE")
        val activate = taskOf(store.seller, "ACTIVATE_SERVICE")
        assertThat(activate["productName"].asString()).isEqualTo("Servicio Premium")

        mockMvc.perform(post("/api/v1/subscriptions/${activate["subscriptionId"].asString()}/activate").with(csrf()).cookie(store.seller)
            .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk)
        assertThat(types(store.seller)).isEmpty()
        assertThat(jdbc.queryForObject("select count(*) from seller_tasks where business_id = ? and status = 'RESOLVED'", Int::class.java, businessIdOf(email))).isEqualTo(2)
        val closed = history(store.seller)["items"].values().toList()
        assertThat(closed.map { it["resolutionAction"].asString() }).containsExactly("SUBSCRIPTION_ACTIVATED", "PAYMENT_VERIFIED")
        assertThat(closed.map { it["resolvedBy"]["userId"].asString() }).containsOnly(userIdOf(email).toString())

        mockMvc.perform(get("/api/v1/tasks/summary").cookie(store.seller)).andExpect(jsonPath("$.open").value(0))
    }

    @Test
    fun `avisos manuales y cupos por liberar se vuelven tareas`() {
        val store = fx.create(registerSeller(), capacity = 2)
        val orderId = fx.paidOrder(store, phone = "911100002")
        val sub = jdbc.queryForObject("select id::text from customer_subscriptions where order_id = ?::uuid", String::class.java, orderId)!!
        val start = Instant.now().minus(Duration.ofDays(28)).plus(Duration.ofHours(2))
        mockMvc.perform(post("/api/v1/subscriptions/$sub/activate").with(csrf()).cookie(store.seller).contentType(MediaType.APPLICATION_JSON)
            .content("""{"startAt":"$start"}""")).andExpect(status().isOk)
        renewalJob.openDueRequests()
        jdbc.update("update renewal_notifications set scheduled_at = now() - interval '1 hour' where customer_id = (select customer_id from customer_subscriptions where id = ?::uuid) and notification_type = 'REMINDER_BEFORE'", sub)

        val notice = taskOf(store.seller, "SEND_RENEWAL_NOTICE")
        assertThat(notice["detail"].asString()).isEqualTo("Recordatorio previo")
        val notificationId = jdbc.queryForObject("select entity_id from seller_tasks where id = ?::uuid", UUID::class.java, notice["id"].asString())
        mockMvc.perform(post("/api/v1/renewals/notifications/$notificationId/sent").with(csrf()).cookie(store.seller)).andExpect(status().isOk)
        assertThat(types(store.seller)).doesNotContain("SEND_RENEWAL_NOTICE")

        // El cliente no renueva: el cupo queda por liberar recién cuando termina su acceso.
        jdbc.update("update capacity_allocations set allocation_status = 'RELEASE_PENDING' where customer_subscription_id = ?::uuid", sub)
        assertThat(types(store.seller)).doesNotContain("RELEASE_ACCESS")
        jdbc.update("update customer_subscriptions set expires_at = now() - interval '1 hour' where id = ?::uuid", sub)
        expirationJob.expireOverdue()
        assertThat(taskOf(store.seller, "RELEASE_ACCESS")["subscriptionId"].asString()).isEqualTo(sub)
        mockMvc.perform(post("/api/v1/subscriptions/$sub/release-capacity").with(csrf()).cookie(store.seller)).andExpect(status().isOk)
        assertThat(types(store.seller)).doesNotContain("RELEASE_ACCESS")
        val closed = history(store.seller)["items"].values().toList()
        assertThat(closed.map { it["resolutionAction"].asString() }).contains("CAPACITY_RELEASED", "NOTIFICATION_SENT")
        assertThat(closed.filter { it["resolutionAction"].asString() in setOf("CAPACITY_RELEASED", "NOTIFICATION_SENT") }
            .all { !it["resolvedBy"].isNull }).isTrue()
    }

    @Test
    fun `cada colaborador ve sus tipos y solo puede tomar o soltar sus tareas`() {
        val ownerEmail = uniqueEmail()
        val store = fx.create(registerSeller(email = ownerEmail))
        reportedOrder(store, "911100003")
        fx.paidOrder(store, phone = "911100004")
        val (ordersEmail, orders) = staffOf(store.seller, ownerEmail, """["ORDERS"]""")
        val (customersEmail, customers) = staffOf(store.seller, ownerEmail, """["CUSTOMERS"]""")

        assertThat(types(store.seller)).containsExactlyInAnyOrder("VERIFY_PAYMENT", "ACTIVATE_SERVICE")
        assertThat(types(orders)).containsExactly("VERIFY_PAYMENT")
        assertThat(types(customers)).containsExactly("ACTIVATE_SERVICE")

        val verifyId = taskOf(orders, "VERIFY_PAYMENT")["id"].asString()
        val activateId = taskOf(customers, "ACTIVATE_SERVICE")["id"].asString()
        // No puede asignar a otros ni ver tareas de secciones ajenas.
        assign(orders, verifyId, userIdOf(ownerEmail)).andExpect(status().isForbidden)
        assign(orders, activateId, userIdOf(ordersEmail)).andExpect(status().isNotFound)
        assign(orders, verifyId, userIdOf(ordersEmail)).andExpect(status().isOk)
        assertThat(tasks(orders, "MINE")["items"].size()).isEqualTo(1)
        mockMvc.perform(get("/api/v1/tasks/summary").cookie(orders)).andExpect(jsonPath("$.mine").value(1))

        // El titular reasigna, pero solo a quien tiene acceso a esa sección.
        assign(store.seller, verifyId, userIdOf(customersEmail)).andExpect(status().isBadRequest)
        assign(store.seller, activateId, userIdOf(customersEmail)).andExpect(status().isOk)
        assertThat(taskOf(store.seller, "ACTIVATE_SERVICE")["assignee"]["fullName"].asString()).isEqualTo("Luis Apoyo")
        assertThat(tasks(store.seller, "UNASSIGNED")["items"].size()).isZero()

        // Al quitarle la sección (o el acceso) deja de ser responsable.
        val memberId = jdbc.queryForObject("select m.id from business_members m join users u on u.id = m.user_id where u.email = ?", UUID::class.java, customersEmail)
        mockMvc.perform(
            put("/api/v1/business/me/staff/$memberId/permissions").with(csrf()).cookie(store.seller).contentType(MediaType.APPLICATION_JSON)
                .content("""{"permissions":["ORDERS"]}"""),
        ).andExpect(status().isOk)
        assertThat(taskOf(store.seller, "ACTIVATE_SERVICE")["assignee"].isNull).isTrue()
    }

    @Test
    fun `las tareas de otro negocio no se ven ni se asignan`() {
        val storeA = fx.create(registerSeller())
        val storeB = fx.create(registerSeller())
        reportedOrder(storeA, "911100005")
        val taskA = taskOf(storeA.seller, "VERIFY_PAYMENT")["id"].asString()
        assertThat(types(storeB.seller)).isEmpty()
        assign(storeB.seller, taskA, null).andExpect(status().isNotFound)
    }

    @Test
    fun `el historial guarda al autor real distinto del asignado y del lector respetando negocio y permisos`() {
        val email = uniqueEmail()
        val store = fx.create(registerSeller(email = email))
        reportedOrder(store, "911100006")
        val task = taskOf(store.seller, "VERIFY_PAYMENT")
        assign(store.seller, task["id"].asString(), userIdOf(email)).andExpect(status().isOk)
        val (staffEmail, staff) = staffOf(store.seller, email, """["ORDERS"]""")
        val (_, customersOnly) = staffOf(store.seller, email, """["CUSTOMERS"]""")
        val (_, payment) = fx.latestPaymentId(store.seller)
        mockMvc.perform(post("/api/v1/payments/$payment/verify").cookie(staff).with(csrf())).andExpect(status().isOk)

        // Se cierra al confirmar la acción, antes de que alguien consulte de nuevo la bandeja.
        assertThat(jdbc.queryForObject("select status from seller_tasks where id = ?::uuid", String::class.java, task["id"].asString())).isEqualTo("RESOLVED")
        val entry = history(store.seller)["items"][0]
        assertThat(entry["resolvedBy"]["userId"].asString()).isEqualTo(userIdOf(staffEmail).toString())
        assertThat(entry["task"]["assignee"]["userId"].asString()).isEqualTo(userIdOf(email).toString())
        assertThat(entry["resolutionAction"].asString()).isEqualTo("PAYMENT_VERIFIED")
        val auditAt = jdbc.queryForObject("select created_at from audit_logs where business_id = ? and entity_id = ?::uuid and action = 'PAYMENT_VERIFIED'", java.sql.Timestamp::class.java, businessIdOf(email), payment)!!
        assertThat(Instant.parse(entry["resolvedAt"].asString())).isEqualTo(auditAt.toInstant())
        assertThat(history(staff)["totalItems"].asLong()).isEqualTo(1)
        assertThat(history(customersOnly)["totalItems"].asLong()).isZero()
        val another = registerSeller()
        assertThat(history(another)["totalItems"].asLong()).isZero()
        mockMvc.perform(get("/api/v1/tasks/history").cookie(another).param("businessId", businessIdOf(email).toString()))
            .andExpect(jsonPath("$.totalItems").value(0))
        assertThat(history(store.seller)["items"][0]["resolvedAt"].asString()).isEqualTo(entry["resolvedAt"].asString())
    }

    @Test
    fun `rechazar y volver a reportar conserva cada resolucion aunque no se consulte la bandeja entre acciones`() {
        val email = uniqueEmail()
        val store = fx.create(registerSeller(email = email))
        val token = fx.placeOrder(store, "911100007")
        fx.report(token, "RECHAZAR123").andExpect(status().isOk)
        val first = taskOf(store.seller, "VERIFY_PAYMENT")
        val (_, payment) = fx.latestPaymentId(store.seller)
        mockMvc.perform(post("/api/v1/payments/$payment/reject").cookie(store.seller).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("""{"reason":"No se recibió la transferencia"}"""))
            .andExpect(status().isOk)
        fx.report(token, "CORREGIDO123").andExpect(status().isOk)
        val second = taskOf(store.seller, "VERIFY_PAYMENT")
        assertThat(second["id"].asString()).isNotEqualTo(first["id"].asString())
        val (_, correctedPayment) = fx.latestPaymentId(store.seller)
        mockMvc.perform(post("/api/v1/payments/$correctedPayment/verify").cookie(store.seller).with(csrf())).andExpect(status().isOk)
        val closed = history(store.seller)["items"].values().toList()
        assertThat(closed.map { it["resolutionAction"].asString() }).containsExactly("PAYMENT_VERIFIED", "PAYMENT_REJECTED")
        val firstPage = history(store.seller, 0, 1)
        val secondPage = history(store.seller, 1, 1)
        assertThat(firstPage["totalItems"].asLong()).isEqualTo(2)
        assertThat(firstPage["totalPages"].asInt()).isEqualTo(2)
        assertThat(firstPage["items"][0]["task"]["id"].asString()).isNotEqualTo(secondPage["items"][0]["task"]["id"].asString())
    }

    @Test
    fun `un cambio de estado sin auditoria no se atribuye al responsable ni al lector`() {
        val email = uniqueEmail()
        val store = fx.create(registerSeller(email = email))
        reportedOrder(store, "911100008")
        val task = taskOf(store.seller, "VERIFY_PAYMENT")
        assign(store.seller, task["id"].asString(), userIdOf(email)).andExpect(status().isOk)
        jdbc.update("update customer_payments set status = 'CANCELLED' where id = (select entity_id from seller_tasks where id = ?::uuid)", task["id"].asString())
        val entry = history(store.seller)["items"][0]
        assertThat(entry["resolutionAction"].asString()).isEqualTo("STATE_CHANGED")
        assertThat(entry["resolvedBy"].isNull).isTrue()
        assertThat(entry["task"]["assignee"]["userId"].asString()).isEqualTo(userIdOf(email).toString())
    }

    @Test
    fun `una verificacion revertida no cierra la tarea ni registra una resolucion`() {
        val email = uniqueEmail()
        val store = fx.create(registerSeller(email = email))
        reportedOrder(store, "911100009")
        val task = taskOf(store.seller, "VERIFY_PAYMENT")
        val (_, payment) = fx.latestPaymentId(store.seller)
        TransactionTemplate(transactions).executeWithoutResult { tx ->
            verification.verify(businessIdOf(email), userIdOf(email), UUID.fromString(payment))
            tx.setRollbackOnly()
        }
        assertThat(taskOf(store.seller, "VERIFY_PAYMENT")["id"].asString()).isEqualTo(task["id"].asString())
        assertThat(history(store.seller)["totalItems"].asLong()).isZero()
    }

    @Test
    fun `cancelar el pedido registra quien cerro la revision sin afirmar que verifico el pago`() {
        val email = uniqueEmail()
        val store = fx.create(registerSeller(email = email))
        reportedOrder(store, "911100010")
        val task = taskOf(store.seller, "VERIFY_PAYMENT")
        mockMvc.perform(post("/api/v1/orders/${task["orderId"].asString()}/cancel").cookie(store.seller).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("""{"reason":"Cancelado a petición del comprador"}"""))
            .andExpect(status().isOk)
        val entry = history(store.seller)["items"][0]
        assertThat(entry["resolutionAction"].asString()).isEqualTo("ORDER_CANCELLED")
        assertThat(entry["resolvedBy"]["userId"].asString()).isEqualTo(userIdOf(email).toString())
    }

    @Test
    fun `omitir un aviso o desactivar avisos registra al autor sin afirmar que se enviaron`() {
        val email = uniqueEmail()
        val store = fx.create(registerSeller(email = email))
        val order = fx.paidOrder(store, "911100011")
        val sub = jdbc.queryForObject("select id::text from customer_subscriptions where order_id = ?::uuid", String::class.java, order)!!
        val start = Instant.now().minus(Duration.ofDays(28)).plus(Duration.ofHours(2))
        mockMvc.perform(post("/api/v1/subscriptions/$sub/activate").with(csrf()).cookie(store.seller).contentType(MediaType.APPLICATION_JSON)
            .content("""{"startAt":"$start"}""")).andExpect(status().isOk)
        renewalJob.openDueRequests()
        jdbc.update("update renewal_notifications set scheduled_at = now() - interval '1 hour' where customer_id = (select customer_id from customer_subscriptions where id = ?::uuid)", sub)
        val notices = tasks(store.seller)["items"].values().filter { it["type"].asString() == "SEND_RENEWAL_NOTICE" }.toList()
        assertThat(notices).hasSize(2)
        val noticeId = jdbc.queryForObject("select entity_id from seller_tasks where id = ?::uuid", UUID::class.java, notices[0]["id"].asString())!!
        mockMvc.perform(post("/api/v1/renewals/notifications/$noticeId/skip").cookie(store.seller).with(csrf())).andExpect(status().isNoContent)
        mockMvc.perform(put("/api/v1/customers/${notices[1]["customerId"].asString()}/notifications").cookie(store.seller).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("""{"optedOut":true}"""))
            .andExpect(status().isOk)
        val closed = history(store.seller)["items"].values().filter { it["task"]["type"].asString() == "SEND_RENEWAL_NOTICE" }.toList()
        assertThat(closed.map { it["resolutionAction"].asString() }).containsExactlyInAnyOrder("NOTIFICATION_SKIPPED", "NOTIFICATIONS_OPT_OUT")
        assertThat(closed.map { it["resolvedBy"]["userId"].asString() }).containsOnly(userIdOf(email).toString())
        assertThat(jdbc.queryForObject("select count(*) from renewal_notifications where id = ? and delivery_status = 'SKIPPED' and sent_at is null", Int::class.java, noticeId)).isEqualTo(1)
    }

    @Test
    fun `el historial exige autenticacion y limita la paginacion`() {
        mockMvc.perform(get("/api/v1/tasks/history")).andExpect(status().isUnauthorized)
        val owner = registerSeller()
        val result = history(owner, -1, 10000)
        assertThat(result["page"].asInt()).isZero()
        assertThat(result["size"].asInt()).isEqualTo(100)
    }
}
