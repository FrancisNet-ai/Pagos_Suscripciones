package com.nuvyra.support

import com.nuvyra.auth.PasswordResetNotifier
import com.nuvyra.common.ratelimit.RateLimiter
import org.junit.jupiter.api.BeforeEach
import jakarta.servlet.http.Cookie
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Captura los enlaces de recuperación en lugar de enviarlos por correo. */
class CapturingPasswordResetNotifier : PasswordResetNotifier {
    val links = ConcurrentHashMap<String, String>()
    override fun send(email: String, fullName: String, resetLink: String) {
        links[email] = resetLink
    }
}

@TestConfiguration(proxyBeanMethods = false)
class TestBeans {
    @Bean
    @Primary
    fun capturingNotifier() = CapturingPasswordResetNotifier()
}

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig::class, TestBeans::class)
abstract class IntegrationTest {
    companion object {
        /** Todas las pruebas comparten el mismo contexto: la Graph API simulada se inyecta aquí. */
        @JvmStatic
        @DynamicPropertySource
        fun whatsappProperties(registry: DynamicPropertyRegistry) {
            registry.add("app.whatsapp.graph-base-url") { FakeWhatsAppGraph.baseUrl }
        }
    }

    @Autowired
    protected lateinit var mockMvc: MockMvc

    @Autowired
    protected lateinit var rateLimiter: RateLimiter

    @Autowired
    private lateinit var supportJdbc: JdbcTemplate

    @Autowired
    private lateinit var supportPasswordEncoder: PasswordEncoder

    @BeforeEach
    fun resetRateLimits() {
        rateLimiter.clearAll()
    }

    protected fun pngBytes(width: Int = 8, height: Int = 8): ByteArray {
        val image = java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB)
        return java.io.ByteArrayOutputStream().also { javax.imageio.ImageIO.write(image, "png", it) }.toByteArray()
    }

    protected fun uniqueEmail() = "vendedor-${UUID.randomUUID().toString().take(8)}@ejemplo.pe"

    protected fun registerJson(
        email: String = uniqueEmail(),
        password: String = "clave-segura-123",
        businessName: String = "Negocio de prueba",
        phone: String = "987654321",
    ) = """
        {"fullName":"Vendedor Prueba","email":"$email","password":"$password","phone":"$phone",
         "businessName":"$businessName","acceptTerms":true,"acceptPrivacy":true}
    """.trimIndent()

    /**
     * Registra un vendedor y devuelve su cookie de sesión. Por defecto recibe un plan vigente sin límites
     * (las pruebas de otras fases no tratan la facturación SaaS); `withPlan = false` lo deja sin plan.
     */
    protected fun registerSeller(email: String = uniqueEmail(), businessName: String = "Negocio de prueba", withPlan: Boolean = true): Cookie {
        val result = mockMvc.perform(
            post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(registerJson(email = email, businessName = businessName)),
        ).andReturn()
        check(result.response.status == 201) { "Registro falló: ${result.response.contentAsString}" }
        if (withPlan) grantPlan(businessIdOf(email))
        return result.response.getCookie("sid")!!
    }

    protected fun businessIdOf(email: String): UUID = supportJdbc.queryForObject(
        "select b.id from businesses b join users u on u.id = b.owner_id where u.email = ?", UUID::class.java, email,
    )!!

    /** Asigna directamente un plan vigente (equivale a un pago SaaS ya verificado). */
    protected fun grantPlan(businessId: UUID, planId: UUID = testPlan(), days: Long = 30) {
        supportJdbc.update(
            """
            insert into saas_subscriptions (id, business_id, plan_id, status, start_at, expires_at)
            values (gen_random_uuid(), ?, ?, 'ACTIVE', now() - interval '1 day', now() + make_interval(days => ?))
            on conflict (business_id) do update set plan_id = excluded.plan_id, status = 'ACTIVE',
              start_at = excluded.start_at, expires_at = excluded.expires_at, grace_until = null
            """.trimIndent(),
            businessId, planId, days.toInt(),
        )
    }

    /** Plan de prueba: todas las funciones y sin límites. */
    protected fun testPlan(): UUID {
        supportJdbc.update(
            "insert into saas_plans (name, price, billing_period) values ('Plan de prueba ilimitado', 10, 'MONTHLY') on conflict (name) do nothing",
        )
        return supportJdbc.queryForObject("select id from saas_plans where name = 'Plan de prueba ilimitado'", UUID::class.java)!!
    }

    /** Crea (si hace falta) un SUPER_ADMIN y devuelve su cookie de sesión. */
    protected fun loginSuperAdmin(email: String = "admin-${UUID.randomUUID().toString().take(8)}@plataforma.pe"): Cookie {
        supportJdbc.update(
            """
            insert into users (id, full_name, email, password_hash, phone, platform_role, status)
            values (gen_random_uuid(), 'Administración', ?, ?, '+51900000000', 'SUPER_ADMIN', 'ACTIVE') on conflict (email) do nothing
            """.trimIndent(),
            email, supportPasswordEncoder.encode("clave-admin-segura-1"),
        )
        val result = mockMvc.perform(
            post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"clave-admin-segura-1"}"""),
        ).andReturn()
        check(result.response.status == 200) { "Login de admin falló: ${result.response.contentAsString}" }
        return result.response.getCookie("sid")!!
    }
}
