package com.nuvyra.common

import com.nuvyra.common.config.AppProperties
import com.nuvyra.common.config.ProductionSafetyCheck
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ProductionSafetyCheckTest {
    private fun props(
        renewal: String = "Hq8n1xZ0pV4mL7cT2wR9sK5yB3dF6gJ0aE1uI8oP",
        key: String = "",
        cookieSecure: Boolean = true,
        url: String = "https://tienda.nuvyra.pe",
        adminPassword: String? = null,
    ) = AppProperties(
        publicBaseUrl = url,
        session = AppProperties.Session(cookieSecure = cookieSecure),
        renewals = AppProperties.Renewals(tokenSecret = renewal),
        secrets = AppProperties.Secrets(encryptionKey = key),
        bootstrap = AppProperties.Bootstrap(superAdminPassword = adminPassword),
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
}
