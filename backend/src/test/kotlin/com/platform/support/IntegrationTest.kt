package com.platform.support

import com.platform.auth.PasswordResetNotifier
import com.platform.common.ratelimit.RateLimiter
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.ActiveProfiles
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
    @Autowired
    protected lateinit var mockMvc: MockMvc

    @Autowired
    protected lateinit var rateLimiter: RateLimiter

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

    /** Registra un vendedor y devuelve su cookie de sesión. */
    protected fun registerSeller(email: String = uniqueEmail(), businessName: String = "Negocio de prueba"): Cookie {
        val result = mockMvc.perform(
            post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(registerJson(email = email, businessName = businessName)),
        ).andReturn()
        check(result.response.status == 201) { "Registro falló: ${result.response.contentAsString}" }
        return result.response.getCookie("sid")!!
    }
}
