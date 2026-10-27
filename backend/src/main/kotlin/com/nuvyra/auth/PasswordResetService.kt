package com.nuvyra.auth

import com.nuvyra.common.audit.AuditActions
import com.nuvyra.common.audit.AuditService
import com.nuvyra.common.config.AppProperties
import com.nuvyra.common.error.ApiException
import com.nuvyra.common.ratelimit.RateLimiter
import com.nuvyra.common.text.SecureTokens
import com.nuvyra.security.SessionService
import com.nuvyra.users.UserRepository
import com.nuvyra.users.UserStatus
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration

@Service
class PasswordResetService(
    private val users: UserRepository,
    private val tokens: PasswordResetTokenRepository,
    private val sessionService: SessionService,
    private val notifier: PasswordResetNotifier,
    private val passwordEncoder: PasswordEncoder,
    private val rateLimiter: RateLimiter,
    private val audit: AuditService,
    private val properties: AppProperties,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Responde igual exista o no la cuenta, para no revelar qué correos están registrados. */
    @Transactional
    fun request(rawEmail: String, ip: String) {
        val email = AuthService.normalizeEmail(rawEmail)
        rateLimiter.check("reset:ip:$ip", limit = 10, window = Duration.ofMinutes(15))
        if (!rateLimiter.tryAcquire("reset:email:$email", limit = 3, window = Duration.ofHours(1))) return

        val user = users.findByEmail(email)?.takeIf { it.status == UserStatus.ACTIVE } ?: return
        val now = clock.instant()
        tokens.invalidateAllForUser(user.id, now)
        val token = SecureTokens.newToken()
        tokens.save(
            PasswordResetToken(
                userId = user.id,
                tokenHash = SecureTokens.sha256Hex(token),
                expiresAt = now.plus(properties.passwordReset.tokenTtl),
                createdAt = now,
            ),
        )
        audit.record(AuditActions.PASSWORD_RESET_REQUESTED, actorId = user.id, ipAddress = ip)
        val link = "${properties.publicBaseUrl.trimEnd('/')}/restablecer?token=$token"
        try {
            notifier.send(user.email, user.fullName, link)
        } catch (ex: Exception) {
            // No se revela el fallo al solicitante; queda registrado para soporte.
            log.error("No se pudo enviar el correo de recuperación", ex)
        }
    }

    @Transactional
    fun confirm(rawToken: String, newPassword: String, ip: String) {
        rateLimiter.check("reset-confirm:ip:$ip", limit = 20, window = Duration.ofMinutes(15))
        AuthService.ensurePasswordFitsBcrypt(newPassword, "newPassword")
        val now = clock.instant()
        val token = tokens.findByTokenHash(SecureTokens.sha256Hex(rawToken))
            ?.takeIf { it.usedAt == null && now.isBefore(it.expiresAt) }
            ?: throw ApiException.badRequest("INVALID_RESET_TOKEN", "El enlace de recuperación no es válido o ya expiró. Solicita uno nuevo.")
        val user = users.findById(token.userId).orElseThrow { ApiException.badRequest("INVALID_RESET_TOKEN", "El enlace de recuperación no es válido.") }

        token.usedAt = now
        user.passwordHash = passwordEncoder.encode(newPassword)!!
        sessionService.revokeAll(user.id)
        audit.record(AuditActions.PASSWORD_RESET_COMPLETED, actorId = user.id, ipAddress = ip)
    }
}
