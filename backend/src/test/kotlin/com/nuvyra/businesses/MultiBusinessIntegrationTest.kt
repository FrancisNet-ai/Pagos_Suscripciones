package com.nuvyra.businesses

import com.nuvyra.support.IntegrationTest
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import java.util.UUID

class MultiBusinessIntegrationTest : IntegrationTest() {
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var objectMapper: ObjectMapper

    private fun json(cookie: Cookie, path: String) = objectMapper.readTree(mockMvc.perform(get(path).cookie(cookie)).andReturn().response.contentAsString)

    private fun switchTo(cookie: Cookie, businessId: Any) = mockMvc.perform(
        post("/api/v1/auth/business").with(csrf()).cookie(cookie).contentType(MediaType.APPLICATION_JSON).content("""{"businessId":"$businessId"}"""),
    )

    private fun createProduct(cookie: Cookie, name: String) = mockMvc.perform(
        post("/api/v1/products").with(csrf()).cookie(cookie).contentType(MediaType.APPLICATION_JSON)
            .content("""{"name":"$name","productType":"ONE_TIME","price":10,"capacityEnabled":false,"available":true}"""),
    ).andExpect(status().isCreated)

    @Test
    fun `una cuenta crea un segundo negocio, cambia entre ellos y cada uno conserva sus datos`() {
        val email = uniqueEmail()
        val cookie = registerSeller(email = email, businessName = "Primer Negocio")
        val first = businessIdOf(email)
        createProduct(cookie, "Producto del primero")

        mockMvc.perform(
            post("/api/v1/auth/businesses").with(csrf()).cookie(cookie).contentType(MediaType.APPLICATION_JSON).content("""{"businessName":"Segundo Negocio"}"""),
        ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.business.businessName").value("Segundo Negocio"))
            .andExpect(jsonPath("$.business.role").value("OWNER"))
            .andExpect(jsonPath("$.memberships.length()").value(2))
        // El negocio nuevo no tiene plan: solo lectura, y no ve productos del otro.
        assertThat(json(cookie, "/api/v1/products")["totalItems"].asInt()).isZero()
        mockMvc.perform(get("/api/v1/business/me").cookie(cookie)).andExpect(jsonPath("$.businessName").value("Segundo Negocio"))

        switchTo(cookie, first).andExpect(status().isOk).andExpect(jsonPath("$.business.id").value(first.toString()))
        assertThat(json(cookie, "/api/v1/products")["items"][0]["name"].asString()).isEqualTo("Producto del primero")
        // No se puede cambiar a un negocio ajeno.
        val stranger = registerSeller()
        val strangerEmail = jdbc.queryForObject("select u.email from users u join user_sessions s on s.user_id = u.id order by s.created_at desc limit 1", String::class.java)!!
        switchTo(cookie, businessIdOf(strangerEmail)).andExpect(status().isNotFound)
        switchTo(stranger, first).andExpect(status().isNotFound)
    }

    @Test
    fun `una cuenta existente se une como colaboradora iniciando sesion y al revocarla conserva su propio negocio`() {
        val ownerEmail = uniqueEmail()
        val owner = registerSeller(email = ownerEmail, businessName = "Negocio Titular")
        val businessA = businessIdOf(ownerEmail)
        jdbc.update("insert into saas_plans (name, price, billing_period, admin_user_limit) values ('Plan multi 3', 10, 'MONTHLY', 3) on conflict (name) do nothing")
        grantPlan(businessA, jdbc.queryForObject("select id from saas_plans where name = 'Plan multi 3'", UUID::class.java)!!)
        val helperEmail = uniqueEmail()
        val helper = registerSeller(email = helperEmail, businessName = "Negocio Propio")
        val businessB = businessIdOf(helperEmail)

        val body = mockMvc.perform(
            post("/api/v1/business/me/staff/invitations").with(csrf()).cookie(owner).contentType(MediaType.APPLICATION_JSON)
                .content("""{"fullName":"Ayuda","email":"$helperEmail","permissions":["ORDERS"]}"""),
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        val token = objectMapper.readTree(body)["link"].asString().substringAfterLast('/')

        mockMvc.perform(get("/api/v1/public/invitations/$token")).andExpect(jsonPath("$.existingAccount").value(true))
        // No puede crear otra cuenta con ese correo ni unirse sin sesión o con otra cuenta.
        mockMvc.perform(
            post("/api/v1/public/invitations/$token/accept").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("""{"fullName":"Ayuda","phone":"976543210","password":"clave-colaborador-1","acceptTerms":true,"acceptPrivacy":true}"""),
        ).andExpect(status().isConflict)
        mockMvc.perform(post("/api/v1/public/invitations/$token/join").with(csrf())).andExpect(status().isUnauthorized)
        mockMvc.perform(post("/api/v1/public/invitations/$token/join").with(csrf()).cookie(owner)).andExpect(status().isForbidden)

        mockMvc.perform(post("/api/v1/public/invitations/$token/join").with(csrf()).cookie(helper))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.business.id").value(businessA.toString()))
            .andExpect(jsonPath("$.business.role").value("STAFF"))
            .andExpect(jsonPath("$.memberships.length()").value(2))
        // En el negocio A es colaboradora con su permiso; en el suyo sigue siendo titular.
        mockMvc.perform(get("/api/v1/business/me/payment-methods").cookie(helper)).andExpect(status().isForbidden)
        switchTo(helper, businessB).andExpect(jsonPath("$.business.role").value("OWNER"))
        mockMvc.perform(get("/api/v1/business/me/payment-methods").cookie(helper)).andExpect(status().isOk)
        switchTo(helper, businessA).andExpect(status().isOk)

        // Revocar en A no desactiva la cuenta: la sesión vuelve a su propio negocio.
        val memberId = jdbc.queryForObject(
            "select m.id from business_members m join users u on u.id = m.user_id where u.email = ? and m.business_id = ?", UUID::class.java, helperEmail, businessA,
        )
        mockMvc.perform(post("/api/v1/business/me/staff/$memberId/revoke").with(csrf()).cookie(owner)).andExpect(status().isOk)
        mockMvc.perform(get("/api/v1/auth/me").cookie(helper))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.business.id").value(businessB.toString()))
            .andExpect(jsonPath("$.memberships.length()").value(1))
        assertThat(jdbc.queryForObject("select status from users where email = ?", String::class.java, helperEmail)).isEqualTo("ACTIVE")
        // Ya miembro (revocado): se reactiva en lugar de invitar de nuevo.
        mockMvc.perform(
            post("/api/v1/business/me/staff/invitations").with(csrf()).cookie(owner).contentType(MediaType.APPLICATION_JSON)
                .content("""{"fullName":"Ayuda","email":"$helperEmail","permissions":["ORDERS"]}"""),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `la cuenta de superadministracion no crea negocios`() {
        val admin = loginSuperAdmin()
        mockMvc.perform(
            post("/api/v1/auth/businesses").with(csrf()).cookie(admin).contentType(MediaType.APPLICATION_JSON).content("""{"businessName":"Negocio admin"}"""),
        ).andExpect(status().isForbidden)
    }
}
