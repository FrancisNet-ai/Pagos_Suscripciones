package com.nuvyra.staff

import com.nuvyra.support.IntegrationTest
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import java.util.UUID

class StaffIntegrationTest : IntegrationTest() {
    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var objectMapper: ObjectMapper

    /** Vendedor con un plan que admite [seats] usuarios (titular incluido). */
    private fun owner(seats: Int = 3): Pair<String, Cookie> {
        val email = uniqueEmail()
        val cookie = registerSeller(email = email)
        val planName = "Plan equipo $seats"
        jdbc.update(
            "insert into saas_plans (name, price, billing_period, admin_user_limit) values (?, 10, 'MONTHLY', ?) on conflict (name) do nothing",
            planName, seats,
        )
        grantPlan(businessIdOf(email), jdbc.queryForObject("select id from saas_plans where name = ?", UUID::class.java, planName)!!)
        return email to cookie
    }

    private fun invite(owner: Cookie, email: String = uniqueEmail(), permissions: String = """["PRODUCTS","ORDERS"]""") =
        mockMvc.perform(
            post("/api/v1/business/me/staff/invitations").with(csrf()).cookie(owner).contentType(MediaType.APPLICATION_JSON)
                .content("""{"fullName":"Lucía Colaboradora","email":"$email","permissions":$permissions}"""),
        )

    private fun tokenOf(owner: Cookie, email: String = uniqueEmail(), permissions: String = """["PRODUCTS","ORDERS"]"""): String {
        val body = invite(owner, email, permissions).andExpect(status().isCreated).andReturn().response.contentAsString
        val link = objectMapper.readTree(body)["link"].asString()
        assertThat(link).contains("/invitacion/")
        return link.substringAfterLast('/')
    }

    private fun accept(token: String, password: String = "clave-colaborador-1") = mockMvc.perform(
        post("/api/v1/public/invitations/$token/accept").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("""{"fullName":"Lucía Ramos","phone":"976543210","password":"$password","acceptTerms":true,"acceptPrivacy":true}"""),
    )

    private fun staff(owner: Cookie, permissions: String = """["PRODUCTS","ORDERS"]"""): Pair<String, Cookie> {
        val email = uniqueEmail()
        val result = accept(tokenOf(owner, email, permissions)).andExpect(status().isOk).andReturn()
        return email to result.response.getCookie("sid")!!
    }

    private fun memberIdOf(email: String): UUID = jdbc.queryForObject(
        "select m.id from business_members m join users u on u.id = m.user_id where u.email = ?", UUID::class.java, email,
    )!!

    @Test
    fun `el titular invita, el colaborador acepta y entra con sus permisos`() {
        val (ownerEmail, owner) = owner()
        val staffEmail = uniqueEmail()
        val token = tokenOf(owner, staffEmail)

        mockMvc.perform(get("/api/v1/public/invitations/$token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value(staffEmail))
            .andExpect(jsonPath("$.permissions.length()").value(2))
        mockMvc.perform(get("/api/v1/business/me/staff").cookie(owner))
            .andExpect(jsonPath("$.invitations.length()").value(1))
            .andExpect(jsonPath("$.seatsUsed").value(2))
            .andExpect(jsonPath("$.seatLimit").value(3))

        val accepted = accept(token).andExpect(status().isOk)
            .andExpect(jsonPath("$.user.roles[0]").value("BUSINESS_STAFF"))
            .andExpect(jsonPath("$.business.role").value("STAFF"))
            .andReturn()
        val staff = accepted.response.getCookie("sid")!!

        mockMvc.perform(get("/api/v1/auth/me").cookie(staff))
            .andExpect(jsonPath("$.business.id").value(businessIdOf(ownerEmail).toString()))
            .andExpect(jsonPath("$.business.permissions.length()").value(2))
        // El enlace es de un solo uso.
        accept(token).andExpect(status().isGone)
        mockMvc.perform(get("/api/v1/public/invitations/$token")).andExpect(status().isGone)
        mockMvc.perform(get("/api/v1/business/me/staff").cookie(owner))
            .andExpect(jsonPath("$.members.length()").value(2))
            .andExpect(jsonPath("$.invitations.length()").value(0))
    }

    @Test
    fun `el colaborador solo entra a las secciones permitidas`() {
        val (_, owner) = owner()
        val (_, staff) = staff(owner, """["PRODUCTS"]""")

        mockMvc.perform(get("/api/v1/products").cookie(staff)).andExpect(status().isOk)
        mockMvc.perform(get("/api/v1/business/me").cookie(staff)).andExpect(status().isOk)
        mockMvc.perform(get("/api/v1/orders").cookie(staff))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("STAFF_FORBIDDEN"))
        for (path in listOf("/api/v1/customers", "/api/v1/subscriptions/expiring", "/api/v1/reports/dashboard", "/api/v1/renewals/overview")) {
            mockMvc.perform(get(path).cookie(staff)).andExpect(status().isForbidden)
        }
        // Exclusivo del titular aunque tenga secciones.
        for (path in listOf("/api/v1/billing", "/api/v1/business/me/staff", "/api/v1/business/me/payment-methods", "/api/v1/business/me/whatsapp")) {
            mockMvc.perform(get(path).cookie(staff)).andExpect(status().isForbidden)
        }
        mockMvc.perform(
            put("/api/v1/business/me").with(csrf()).cookie(staff).contentType(MediaType.APPLICATION_JSON)
                .content("""{"businessName":"Cambio indebido","timeZone":"America/Lima"}"""),
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `con renovaciones puede ver los ajustes pero no cambiarlos`() {
        val (_, owner) = owner()
        val (_, staff) = staff(owner, """["RENEWALS"]""")
        mockMvc.perform(get("/api/v1/renewals/settings").cookie(staff)).andExpect(status().isOk)
        mockMvc.perform(get("/api/v1/renewals/overview").cookie(staff)).andExpect(status().isOk)
        // Cupos por liberar: listado de suscripciones, sin acceso al resto de Clientes.
        mockMvc.perform(get("/api/v1/subscriptions").param("filter", "RELEASE_PENDING").cookie(staff)).andExpect(status().isOk)
        mockMvc.perform(get("/api/v1/customers").cookie(staff)).andExpect(status().isForbidden)
        mockMvc.perform(
            put("/api/v1/renewals/settings").with(csrf()).cookie(staff).contentType(MediaType.APPLICATION_JSON).content("{}"),
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `con clientes puede elegir productos pero no editarlos`() {
        val (_, owner) = owner()
        val (_, staff) = staff(owner, """["CUSTOMERS"]""")
        mockMvc.perform(get("/api/v1/products").cookie(staff)).andExpect(status().isOk)
        mockMvc.perform(get("/api/v1/customers").cookie(staff)).andExpect(status().isOk)
        mockMvc.perform(
            post("/api/v1/products").with(csrf()).cookie(staff).contentType(MediaType.APPLICATION_JSON).content("{}"),
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `el titular cambia permisos y revoca el acceso al instante`() {
        val (_, owner) = owner()
        val (staffEmail, staff) = staff(owner, """["PRODUCTS"]""")
        val memberId = memberIdOf(staffEmail)

        mockMvc.perform(
            put("/api/v1/business/me/staff/$memberId/permissions").with(csrf()).cookie(owner).contentType(MediaType.APPLICATION_JSON)
                .content("""{"permissions":["ORDERS"]}"""),
        ).andExpect(status().isOk)
        mockMvc.perform(get("/api/v1/products").cookie(staff)).andExpect(status().isForbidden)
        mockMvc.perform(get("/api/v1/orders").cookie(staff)).andExpect(status().isOk)

        mockMvc.perform(post("/api/v1/business/me/staff/$memberId/revoke").with(csrf()).cookie(owner)).andExpect(status().isOk)
        mockMvc.perform(get("/api/v1/orders").cookie(staff)).andExpect(status().isUnauthorized)
        mockMvc.perform(
            post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$staffEmail","password":"clave-colaborador-1"}"""),
        ).andExpect(status().isUnauthorized)

        mockMvc.perform(post("/api/v1/business/me/staff/$memberId/reactivate").with(csrf()).cookie(owner)).andExpect(status().isOk)
        val login = mockMvc.perform(
            post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$staffEmail","password":"clave-colaborador-1"}"""),
        ).andExpect(status().isOk).andReturn()
        mockMvc.perform(get("/api/v1/orders").cookie(login.response.getCookie("sid")!!)).andExpect(status().isOk)
    }

    @Test
    fun `el acceso del titular no se modifica`() {
        val (ownerEmail, owner) = owner()
        mockMvc.perform(post("/api/v1/business/me/staff/${memberIdOf(ownerEmail)}/revoke").with(csrf()).cookie(owner))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("OWNER_IMMUTABLE"))
    }

    @Test
    fun `el limite de usuarios del plan cuenta invitaciones pendientes`() {
        val (_, owner) = owner(seats = 2)
        val token = tokenOf(owner)
        invite(owner).andExpect(status().isConflict).andExpect(jsonPath("$.code").value("SEAT_LIMIT_REACHED"))
        accept(token).andExpect(status().isOk)
        invite(owner).andExpect(status().isConflict)

        val (_, solo) = owner(seats = 1)
        invite(solo).andExpect(status().isConflict).andExpect(jsonPath("$.code").value("SEAT_LIMIT_REACHED"))
    }

    @Test
    fun `cancelar una invitacion libera el lugar e invalida el enlace`() {
        val (_, owner) = owner(seats = 2)
        val token = tokenOf(owner)
        val id = objectMapper.readTree(
            mockMvc.perform(get("/api/v1/business/me/staff").cookie(owner)).andReturn().response.contentAsString,
        )["invitations"][0]["id"].asString()
        mockMvc.perform(delete("/api/v1/business/me/staff/invitations/$id").with(csrf()).cookie(owner)).andExpect(status().isOk)
        accept(token).andExpect(status().isGone)
        invite(owner).andExpect(status().isCreated)
    }

    @Test
    fun `renovar el enlace invalida el anterior`() {
        val (_, owner) = owner()
        val old = tokenOf(owner)
        val id = jdbc.queryForObject(
            "select id from staff_invitations where token_hash = encode(sha256(convert_to(?, 'UTF8')), 'hex')", UUID::class.java, old,
        )
        val body = mockMvc.perform(post("/api/v1/business/me/staff/invitations/$id/renew").with(csrf()).cookie(owner))
            .andExpect(status().isOk).andReturn().response.contentAsString
        val fresh = objectMapper.readTree(body)["link"].asString().substringAfterLast('/')
        mockMvc.perform(get("/api/v1/public/invitations/$old")).andExpect(status().isGone)
        mockMvc.perform(get("/api/v1/public/invitations/$fresh")).andExpect(status().isOk)
    }

    @Test
    fun `invitaciones vencidas o con correos existentes se rechazan`() {
        val (ownerEmail, owner) = owner()
        invite(owner, email = ownerEmail).andExpect(status().isBadRequest)
        val email = uniqueEmail()
        val token = tokenOf(owner, email)
        invite(owner, email = email).andExpect(status().isBadRequest)
        jdbc.update("update staff_invitations set expires_at = now() - interval '1 minute' where email = ?", email)
        mockMvc.perform(get("/api/v1/public/invitations/$token")).andExpect(status().isGone)
        accept(token).andExpect(status().isGone)
        mockMvc.perform(get("/api/v1/public/invitations/${"x".repeat(43)}")).andExpect(status().isGone)
        invite(owner, permissions = "[]").andExpect(status().isBadRequest)
    }

    @Test
    fun `sin plan vigente no se suman colaboradores pero si se revocan accesos`() {
        val (ownerEmail, owner) = owner()
        val (staffEmail, _) = staff(owner)
        val pendingToken = tokenOf(owner)
        jdbc.update("update saas_subscriptions set status = 'EXPIRED', start_at = now() - interval '90 days', expires_at = now() - interval '60 days', grace_until = null where business_id = ?", businessIdOf(ownerEmail))

        mockMvc.perform(get("/api/v1/business/me/staff").cookie(owner)).andExpect(jsonPath("$.seatLimit").doesNotExist())
        invite(owner).andExpect(status().isForbidden)
        accept(pendingToken).andExpect(status().isConflict).andExpect(jsonPath("$.code").value("PLAN_REQUIRED"))
        mockMvc.perform(post("/api/v1/business/me/staff/${memberIdOf(staffEmail)}/revoke").with(csrf()).cookie(owner)).andExpect(status().isOk)
    }

    @Test
    fun `el titular solo gestiona su propio equipo`() {
        val (_, ownerA) = owner()
        val (_, ownerB) = owner()
        val (staffEmail, _) = staff(ownerA)
        mockMvc.perform(post("/api/v1/business/me/staff/${memberIdOf(staffEmail)}/revoke").with(csrf()).cookie(ownerB))
            .andExpect(status().isNotFound)
        val (_, staffB) = staff(ownerB, """["PRODUCTS"]""")
        mockMvc.perform(get("/api/v1/products").cookie(staffB))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(0))
    }

    @Test
    fun `aprobar pagos es un permiso aparte de atender pedidos`() {
        val ownerEmail = uniqueEmail()
        val ownerCookie = registerSeller(email = ownerEmail)
        jdbc.update(
            "insert into saas_plans (name, price, billing_period, admin_user_limit) values ('Plan equipo 5', 10, 'MONTHLY', 5) on conflict (name) do nothing",
        )
        grantPlan(businessIdOf(ownerEmail), jdbc.queryForObject("select id from saas_plans where name = 'Plan equipo 5'", UUID::class.java)!!)
        val fx = com.nuvyra.support.StoreFixture(mockMvc)
        val store = fx.create(ownerCookie)
        fx.report(fx.placeOrder(store, "914500001")).andExpect(status().isOk)
        val (orderId, paymentId) = fx.latestPaymentId(ownerCookie)
        val (_, attends) = staff(ownerCookie, """["ORDERS"]""")
        val (_, approves) = staff(ownerCookie, """["PAYMENTS"]""")

        // Atiende pedidos: los ve, pero no aprueba dinero.
        mockMvc.perform(get("/api/v1/orders/$orderId").cookie(attends)).andExpect(status().isOk)
        mockMvc.perform(post("/api/v1/payments/$paymentId/verify").with(csrf()).cookie(attends))
            .andExpect(status().isForbidden).andExpect(jsonPath("$.code").value("STAFF_FORBIDDEN"))
        // Aprueba pagos: ve el pedido y aprueba, pero no cancela pedidos.
        mockMvc.perform(get("/api/v1/orders").cookie(approves)).andExpect(status().isOk)
        mockMvc.perform(
            post("/api/v1/orders/$orderId/cancel").with(csrf()).cookie(approves).contentType(MediaType.APPLICATION_JSON).content("""{"reason":"Prueba de permisos"}"""),
        ).andExpect(status().isForbidden)
        mockMvc.perform(post("/api/v1/payments/$paymentId/verify").with(csrf()).cookie(approves)).andExpect(status().isOk)
        // Reembolsar sigue siendo exclusivo del titular.
        mockMvc.perform(
            post("/api/v1/payments/$paymentId/refund").with(csrf()).cookie(approves).contentType(MediaType.APPLICATION_JSON).content("""{"reason":"Prueba de permisos"}"""),
        ).andExpect(status().isForbidden)
    }
}
