package com.platform.renewals

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId

class RenewalMessagesTest {
    private val zone = ZoneId.of("America/Lima")
    private val expiresAt = Instant.parse("2026-10-27T02:00:00Z") // 26 de octubre en Lima.

    @Test
    fun `un aviso recuperado despues de su fecha no afirma que vence hoy`() {
        val message = RenewalMessages.reminder("María Pérez", "Servicio", "Tienda", expiresAt, zone, false, "https://example.test/renovar/token", overdue = true)
        assertThat(message).contains("Hola, María.", "venció el 26 de octubre", "https://example.test/renovar/token").doesNotContain("vence hoy")
    }

    @Test
    fun `el aviso del dia usa el estado real y el previo respeta la zona del negocio`() {
        assertThat(RenewalMessages.reminder("María", "Servicio", "Tienda", expiresAt, zone, true, "enlace")).contains("vence hoy")
        assertThat(RenewalMessages.reminder("María", "Servicio", "Tienda", expiresAt, zone, false, "enlace")).contains("vence el 26 de octubre")
    }
}
