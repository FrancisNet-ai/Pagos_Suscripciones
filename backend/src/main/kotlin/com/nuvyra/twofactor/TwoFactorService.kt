package com.nuvyra.twofactor

import com.nuvyra.auth.ClientInfo
import com.nuvyra.common.audit.AuditActions
import com.nuvyra.common.audit.AuditService
import com.nuvyra.common.config.AppProperties
import com.nuvyra.common.error.ApiException
import com.nuvyra.common.ratelimit.RateLimiter
import com.nuvyra.common.text.SecretCipher
import com.nuvyra.common.text.SecureTokens
import com.nuvyra.security.AuthenticatedUser
import com.nuvyra.security.Roles
import com.nuvyra.security.SessionService
import com.nuvyra.users.User
import com.nuvyra.users.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Verificación en dos pasos con una app de autenticación (TOTP). El secreto se cifra con
 * [SecretCipher]; los códigos de recuperación y los desafíos de inicio de sesión solo guardan su hash.
 */
@Service
class TwoFactorService(
    private val users: UserRepository,
    private val recoveryCodes: RecoveryCodeRepository,
    private val challenges: LoginChallengeRepository,
    private val cipher: SecretCipher,
    private val passwordEncoder: PasswordEncoder,
    private val rateLimiter: RateLimiter,
    private val audit: AuditService,
    private val sessionService: SessionService,
    private val properties: AppProperties,
    private val clock: Clock,
    transactionManager: PlatformTransactionManager,
) {
    private val tx = TransactionTemplate(transactionManager)

    /** Solo el superadministrador tiene la verificación obligatoria (configurable fuera de producción). */
    fun required(roles: Set<String>) = properties.security.requireSuperAdminTwoFactor && Roles.SUPER_ADMIN in roles

    @Transactional(readOnly = true)
    fun status(principal: AuthenticatedUser): TwoFactorStatusResponse {
        val user = user(principal.userId)
        return TwoFactorStatusResponse(
            enabled = user.twoFactorEnabled,
            enabledAt = user.totpEnabledAt,
            recoveryCodesLeft = if (user.twoFactorEnabled) recoveryCodes.countByUserIdAndUsedAtIsNull(user.id) else 0,
            required = required(principal.roles),
        )
    }

    /** Genera el secreto y el QR. No se activa hasta confirmar un código en [enable]. */
    @Transactional
    fun startSetup(principal: AuthenticatedUser, password: String): TwoFactorSetupResponse {
        rateLimiter.check("2fa-setup:${principal.userId}", limit = 5, window = Duration.ofMinutes(15))
        val user = user(principal.userId)
        requirePassword(user, password)
        if (user.twoFactorEnabled) {
            throw ApiException.conflict("TWO_FACTOR_ALREADY_ENABLED", "La verificación en dos pasos ya está activa.")
        }
        val secret = Totp.newSecret()
        user.totpPendingSecretEncrypted = cipher.encrypt(secret, aad(user.id))
        val uri = Totp.otpauthUri(secret, ISSUER, user.email)
        return TwoFactorSetupResponse(secret.chunked(4).joinToString(" "), uri, QrSvg.dataUrl(uri))
    }

    /** Confirma el primer código, activa la verificación, entrega los códigos de recuperación y cierra las demás sesiones. */
    @Transactional
    fun enable(principal: AuthenticatedUser, code: String, client: ClientInfo): TwoFactorEnabledResponse {
        rateLimiter.check("2fa-verify:${principal.userId}", limit = 10, window = Duration.ofMinutes(15))
        val user = user(principal.userId)
        val pending = user.totpPendingSecretEncrypted ?: throw ApiException.conflict(
            "TWO_FACTOR_SETUP_NOT_STARTED", "Primero genera el código QR para tu app de autenticación.",
        )
        val step = Totp.matchingStep(cipher.decrypt(pending, aad(user.id)), normalize(code), clock.instant().epochSecond)
            ?: throw ApiException.invalidField("code", WRONG_CODE)
        user.totpSecretEncrypted = pending
        user.totpPendingSecretEncrypted = null
        user.totpEnabledAt = clock.instant()
        user.totpLastStep = step
        val codes = replaceRecoveryCodes(user.id)
        // Las sesiones abiertas antes no pasaron por el segundo factor.
        val closed = sessionService.revokeOthers(user.id, principal.sessionId)
        audit.record(AuditActions.TWO_FACTOR_ENABLED, actorId = user.id, businessId = principal.businessId, ipAddress = client.ip,
            metadata = mapOf("otherSessionsClosed" to closed))
        return TwoFactorEnabledResponse(codes, closed)
    }

    @Transactional
    fun disable(principal: AuthenticatedUser, password: String, code: String, client: ClientInfo) {
        if (required(principal.roles)) {
            throw ApiException.forbidden("La cuenta de superadministración debe mantener la verificación en dos pasos.")
        }
        rateLimiter.check("2fa-verify:${principal.userId}", limit = 10, window = Duration.ofMinutes(15))
        val user = user(principal.userId)
        if (!user.twoFactorEnabled) return
        requirePassword(user, password)
        verifySecondFactor(user, code, client) ?: throw ApiException.invalidField("code", WRONG_CODE)
        user.totpSecretEncrypted = null
        user.totpPendingSecretEncrypted = null
        user.totpEnabledAt = null
        user.totpLastStep = null
        recoveryCodes.deleteAllForUser(user.id)
        audit.record(AuditActions.TWO_FACTOR_DISABLED, actorId = user.id, businessId = principal.businessId, ipAddress = client.ip)
    }

    /** Invalida los códigos anteriores. Exige un código de la app (no uno de recuperación). */
    @Transactional
    fun regenerateRecoveryCodes(principal: AuthenticatedUser, code: String, client: ClientInfo): RecoveryCodesResponse {
        rateLimiter.check("2fa-verify:${principal.userId}", limit = 10, window = Duration.ofMinutes(15))
        val user = user(principal.userId)
        if (!user.twoFactorEnabled) throw ApiException.conflict("TWO_FACTOR_NOT_ENABLED", "La verificación en dos pasos no está activa.")
        if (!verifyTotp(user, normalize(code))) throw ApiException.invalidField("code", WRONG_CODE)
        val codes = replaceRecoveryCodes(user.id)
        audit.record(AuditActions.TWO_FACTOR_RECOVERY_CODES_REGENERATED, actorId = user.id, businessId = principal.businessId, ipAddress = client.ip)
        return RecoveryCodesResponse(codes)
    }

    /** Tras validar la contraseña: devuelve un token de un solo uso para enviar el segundo factor. */
    @Transactional
    fun createChallenge(userId: UUID): TwoFactorChallengeResponse {
        val token = SecureTokens.newToken()
        val now = clock.instant()
        val challenge = challenges.save(LoginChallenge(userId, SecureTokens.sha256Hex(token), now, now.plus(CHALLENGE_TTL)))
        return TwoFactorChallengeResponse(challengeToken = token, expiresAt = challenge.expiresAt)
    }

    /**
     * Valida el segundo factor del inicio de sesión y devuelve el usuario. Sin transacción externa:
     * cada intento queda contado aunque el código sea incorrecto (máximo [MAX_ATTEMPTS] por desafío).
     */
    fun completeChallenge(token: String, code: String, client: ClientInfo): UUID {
        val userId = tx.execute {
            val challenge = challenges.findByTokenHash(SecureTokens.sha256Hex(token))
                ?.takeIf { it.consumedAt == null && it.attempts < MAX_ATTEMPTS && clock.instant().isBefore(it.expiresAt) }
                ?: throw ApiException(HttpStatus.UNAUTHORIZED, "TWO_FACTOR_CHALLENGE_EXPIRED", "La verificación expiró. Vuelve a ingresar tu contraseña.")
            challenge.attempts += 1
            challenge.userId
        }!!
        rateLimiter.check("2fa-login:$userId", limit = 10, window = Duration.ofMinutes(15))
        val accepted = tx.execute {
            val user = users.findById(userId).orElse(null) ?: return@execute false
            if (verifySecondFactor(user, code, client) == null) return@execute false
            challenges.findByTokenHash(SecureTokens.sha256Hex(token))?.consumedAt = clock.instant()
            true
        }!!
        if (!accepted) {
            audit.record(AuditActions.LOGIN_SECOND_FACTOR_FAILED, actorId = userId, ipAddress = client.ip)
            throw ApiException.invalidField("code", WRONG_CODE)
        }
        return userId
    }

    @Scheduled(cron = "0 37 3 * * *")
    @Transactional
    fun purgeExpiredChallenges() {
        challenges.deleteExpiredBefore(clock.instant().minus(Duration.ofDays(1)))
    }

    /** Acepta un código de la app o uno de recuperación. Devuelve el método usado o `null`. */
    private fun verifySecondFactor(user: User, code: String, client: ClientInfo): String? {
        val normalized = normalize(code)
        if (verifyTotp(user, normalized)) return "TOTP"
        if (normalized.length != RECOVERY_CODE_LENGTH) return null
        val match = recoveryCodes.findByUserIdAndCodeHashAndUsedAtIsNull(user.id, SecureTokens.sha256Hex(normalized)) ?: return null
        match.usedAt = clock.instant()
        audit.record(AuditActions.TWO_FACTOR_RECOVERY_CODE_USED, actorId = user.id, ipAddress = client.ip,
            // La consulta vacía antes los cambios pendientes: el código recién usado ya no cuenta.
            metadata = mapOf("recoveryCodesLeft" to recoveryCodes.countByUserIdAndUsedAtIsNull(user.id)))
        return "RECOVERY_CODE"
    }

    /** Un mismo código (intervalo de 30 s) solo sirve una vez. */
    private fun verifyTotp(user: User, code: String): Boolean {
        val encrypted = user.totpSecretEncrypted ?: return false
        val step = Totp.matchingStep(cipher.decrypt(encrypted, aad(user.id)), code, clock.instant().epochSecond) ?: return false
        if (user.totpLastStep?.let { step <= it } == true) return false
        user.totpLastStep = step
        return true
    }

    private fun replaceRecoveryCodes(userId: UUID): List<String> {
        recoveryCodes.deleteAllForUser(userId)
        val now = clock.instant()
        return List(RECOVERY_CODES) {
            val raw = SecureTokens.readableCode(RECOVERY_CODE_LENGTH)
            recoveryCodes.save(RecoveryCode(userId, SecureTokens.sha256Hex(raw), now))
            raw.chunked(5).joinToString("-")
        }
    }

    private fun requirePassword(user: User, password: String) {
        if (!passwordEncoder.matches(password, user.passwordHash)) {
            throw ApiException.invalidField("password", "La contraseña no es correcta")
        }
    }

    private fun user(id: UUID): User = users.findById(id).orElseThrow { ApiException.unauthorized() }

    /** El secreto se autentica con el id del usuario: copiado a otra fila no se descifra. */
    private fun aad(userId: UUID) = "totp:$userId"

    private fun normalize(code: String) = code.filterNot { it.isWhitespace() || it == '-' }.uppercase()

    companion object {
        const val ISSUER = "Nuvyra"
        const val MAX_ATTEMPTS = 5
        const val RECOVERY_CODES = 10
        const val RECOVERY_CODE_LENGTH = 10
        val CHALLENGE_TTL: Duration = Duration.ofMinutes(5)
        private const val WRONG_CODE = "El código no es correcto. Revisa que la hora de tu celular sea automática."
    }
}

data class TwoFactorStatusResponse(val enabled: Boolean, val enabledAt: Instant?, val recoveryCodesLeft: Long, val required: Boolean)

/** `secret` agrupado de 4 en 4 para escribirlo a mano si no se puede escanear el QR. */
data class TwoFactorSetupResponse(val secret: String, val otpauthUri: String, val qrDataUrl: String)

/** Los códigos de recuperación se muestran una sola vez. */
data class TwoFactorEnabledResponse(val recoveryCodes: List<String>, val otherSessionsClosed: Int)

data class RecoveryCodesResponse(val recoveryCodes: List<String>)

/** Respuesta `202` de `/auth/login` cuando falta el segundo factor. */
data class TwoFactorChallengeResponse(val twoFactorRequired: Boolean = true, val challengeToken: String, val expiresAt: Instant)
