package com.nuvyra.auth

import com.nuvyra.common.audit.AuditService
import com.nuvyra.common.config.AppProperties
import com.nuvyra.common.ratelimit.RateLimiter
import com.nuvyra.security.SessionService
import com.nuvyra.users.User
import com.nuvyra.users.UserRepository
import com.nuvyra.users.UserStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Clock

class PasswordResetServiceTest {
    private val users = mockk<UserRepository>()
    private val tokens = mockk<PasswordResetTokenRepository>(relaxed = true)
    private val notifier = mockk<PasswordResetNotifier>(relaxed = true)
    private val service = PasswordResetService(
        users = users,
        tokens = tokens,
        sessionService = mockk<SessionService>(relaxed = true),
        notifier = notifier,
        passwordEncoder = mockk<PasswordEncoder>(),
        rateLimiter = RateLimiter(Clock.systemUTC()),
        audit = mockk<AuditService>(relaxed = true),
        properties = AppProperties(publicBaseUrl = "https://tudominio.com/"),
        clock = Clock.systemUTC(),
    )

    @Test
    fun `no envia nada si el correo no existe`() {
        every { users.findByEmail("nadie@ejemplo.pe") } returns null
        service.request("Nadie@Ejemplo.pe", "10.0.0.1")
        verify(exactly = 0) { notifier.send(any(), any(), any()) }
    }

    @Test
    fun `no envia enlaces a cuentas deshabilitadas`() {
        every { users.findByEmail("ana@ejemplo.pe") } returns user(UserStatus.DISABLED)
        service.request("ana@ejemplo.pe", "10.0.0.1")
        verify(exactly = 0) { notifier.send(any(), any(), any()) }
    }

    @Test
    fun `envia un enlace con token opaco y guarda solo su hash`() {
        every { users.findByEmail("ana@ejemplo.pe") } returns user(UserStatus.ACTIVE)
        val link = slot<String>()
        val saved = slot<PasswordResetToken>()
        every { tokens.save(capture(saved)) } answers { saved.captured }
        every { notifier.send(any(), any(), capture(link)) } returns Unit

        service.request("ana@ejemplo.pe", "10.0.0.1")

        assertThat(link.captured).startsWith("https://tudominio.com/restablecer?token=")
        val token = link.captured.substringAfter("token=")
        assertThat(saved.captured.tokenHash).hasSize(64).isNotEqualTo(token)
    }

    private fun user(status: UserStatus) = User(
        fullName = "Ana", email = "ana@ejemplo.pe", passwordHash = "{bcrypt}x", phone = "+51987654321", status = status,
    )
}
