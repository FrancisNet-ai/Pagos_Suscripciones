package com.nuvyra.common

import com.nuvyra.common.config.AppProperties
import com.nuvyra.common.config.ProductionSafetyCheck
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ProductionSafetyCheckTest {
    private fun props(
        renewal: String = "Hq8n1xZ0pV4mL7cT2wR9sK5yB3dF6gJ0aE1uI8oP",
        key: String = "u7Vb3Kx9Qm2Lp8Rt4Wz6Yc1Nf5Hd0Gs7Ja2Ek9Mo3Pi=",
        cookieSecure: Boolean = true,
        url: String = "https://tienda.nuvyra.pe",
        adminPassword: String? = null,
        requireTwoFactor: Boolean = true,
        breachedCheck: Boolean = true,
    ) = AppProperties(
        publicBaseUrl = url,
        session = AppProperties.Session(cookieSecure = cookieSecure),
        renewals = AppProperties.Renewals(tokenSecret = renewal),
        secrets = AppProperties.Secrets(encryptionKey = key),
        bootstrap = AppProperties.Bootstrap(superAdminPassword = adminPassword),
        security = AppProperties.Security(requireSuperAdminTwoFactor = requireTwoFactor, breachedPasswordCheck = breachedCheck),
    )

    @Test
    fun `una configuracion de produccion correcta no tiene observaciones`() {
        assertThat(ProductionSafetyCheck.problems(props(), databasePassword = "Kq3!xLp9vT2mWz")).isEmpty()
    }

    @Test
    fun `rechaza las claves publicadas en el repositorio y los valores de ejemplo`() {
        val problems = ProductionSafetyCheck.problems(
            props(renewal = "dev-only-renewal-secret-change-me-0123456789", key = "ZGV2LW9ubHktZW5jcnlwdGlvbi1rZXktMzJieXRlcyE=", adminPassword = "corta"),
            databasePassword = "cambia-esta-clave",
        )
        assertThat(problems).hasSize(4)
        assertThat(ProductionSafetyCheck.problems(props(renewal = "reemplaza-por-un-valor-aleatorio-de-al-menos-32-caracteres"))).hasSize(1)
        assertThat(ProductionSafetyCheck.problems(props(renewal = ""))).containsExactly("APP_RENEWAL_TOKEN_SECRET no está definido")
    }

    @Test
    fun `exige HTTPS y cookies seguras salvo en una prueba local`() {
        assertThat(ProductionSafetyCheck.problems(props(cookieSecure = false, url = "http://tienda.nuvyra.pe"))).hasSize(2)
        assertThat(ProductionSafetyCheck.problems(props(cookieSecure = false, url = "http://localhost:8080"))).isEmpty()
    }

    @Test
    fun `exige la clave de cifrado y la verificacion en dos pasos del superadministrador`() {
        assertThat(ProductionSafetyCheck.problems(props(key = ""))).containsExactly("APP_SECRETS_ENCRYPTION_KEY no está definido")
        assertThat(ProductionSafetyCheck.problems(props(requireTwoFactor = false)))
            .containsExactly("APP_SECURITY_REQUIRE_SUPER_ADMIN_TWO_FACTOR debe ser true en producción")
    }

    @Test
    fun `exige comprobar contrasenas filtradas`() {
        assertThat(ProductionSafetyCheck.problems(props(breachedCheck = false)))
            .containsExactly("APP_SECURITY_BREACHED_PASSWORD_CHECK debe ser true en producción")
    }
}
