package com.platform.auth

import com.platform.businesses.Business
import com.platform.businesses.BusinessService
import com.platform.common.audit.AuditActions
import com.platform.common.audit.AuditService
import com.platform.common.error.ApiException
import com.platform.common.ratelimit.RateLimiter
import com.platform.common.validation.PeruPhone
import com.platform.security.AuthenticatedUser
import com.platform.security.IssuedSession
import com.platform.security.Roles
import com.platform.security.SessionService
import com.platform.users.User
import com.platform.users.UserRepository
import com.platform.users.UserStatus
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration

data class ClientInfo(val ip: String, val userAgent: String?)

data class AuthResult(val response: SessionResponse, val session: IssuedSession)

@Service
class AuthService(
    private val users: UserRepository,
    private val businessService: BusinessService,
    private val sessionService: SessionService,
    private val passwordEncoder: PasswordEncoder,
    private val rateLimiter: RateLimiter,
    private val audit: AuditService,
    private val clock: Clock,
) {
    // Hash válido de una contraseña aleatoria: iguala el tiempo de respuesta cuando el correo no existe.
    private val dummyHash: String = passwordEncoder.encode("dummy-password-for-timing")!!

    @Transactional
    fun register(request: RegisterRequest, client: ClientInfo): AuthResult {
        rateLimiter.check("register:ip:${client.ip}", limit = 10, window = Duration.ofHours(1))
        val email = normalizeEmail(request.email!!)
        val password = request.password!!
        ensurePasswordFitsBcrypt(password, "password")
        val phone = PeruPhone.normalize(request.phone) ?: throw ApiException.invalidField("phone", "Ingresa un celular peruano válido de 9 dígitos")

        if (users.existsByEmail(email)) {
            throw ApiException.conflict("EMAIL_ALREADY_REGISTERED", "Ya existe una cuenta con este correo. Inicia sesión o recupera tu contraseña.")
        }
        val now = clock.instant()
        val user = users.save(
            User(
                fullName = request.fullName!!.trim(),
                email = email,
                passwordHash = passwordEncoder.encode(password)!!,
                phone = phone,
                termsAcceptedAt = now,
                privacyAcceptedAt = now,
            ),
        )
        // El WhatsApp comercial empieza siendo el celular del titular; se puede cambiar en Configuración.
        val business = businessService.createForOwner(user.id, request.businessName!!, whatsappNumber = phone)
        audit.record(
            AuditActions.USER_REGISTERED, actorId = user.id, businessId = business.id,
            entityType = "BUSINESS", entityId = business.id, ipAddress = client.ip,
        )
        val session = sessionService.create(user.id, client.ip, client.userAgent)
        return AuthResult(toResponse(user, setOf(Roles.BUSINESS_OWNER), business), session)
    }

    /** Sin @Transactional: el registro de intentos fallidos debe persistir aunque se lance el error. */
    fun login(request: LoginRequest, client: ClientInfo): AuthResult {
        val email = normalizeEmail(request.email!!)
        rateLimiter.check("login:ip:${client.ip}", limit = 30, window = Duration.ofMinutes(15))
        rateLimiter.check("login:email:$email", limit = 10, window = Duration.ofMinutes(15))

        val user = users.findByEmail(email)
        val passwordMatches = passwordEncoder.matches(request.password!!, user?.passwordHash ?: dummyHash)
        if (user == null || !passwordMatches || user.status != UserStatus.ACTIVE) {
            audit.record(AuditActions.LOGIN_FAILED, actorId = user?.id, metadata = mapOf("reason" to if (user == null) "unknown_email" else "bad_credentials"), ipAddress = client.ip)
            throw ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Correo o contraseña incorrectos.")
        }
        rateLimiter.reset("login:email:$email")
        val session = sessionService.create(user.id, client.ip, client.userAgent)
        val principal = sessionService.authenticate(session.token)!!
        audit.record(AuditActions.LOGIN_SUCCEEDED, actorId = user.id, businessId = principal.businessId, ipAddress = client.ip)
        return AuthResult(describe(principal), session)
    }

    fun logout(principal: AuthenticatedUser, client: ClientInfo) {
        sessionService.revoke(principal.sessionId)
        audit.record(AuditActions.LOGOUT, actorId = principal.userId, businessId = principal.businessId, ipAddress = client.ip)
    }

    @Transactional(readOnly = true)
    fun describe(principal: AuthenticatedUser): SessionResponse {
        val user = users.findById(principal.userId).orElseThrow { ApiException.unauthorized() }
        val business = principal.businessId?.let { businessService.get(it) }
        return toResponse(user, principal.roles, business)
    }

    private fun toResponse(user: User, roles: Set<String>, business: Business?) = SessionResponse(
        user = UserDto(user.id, user.fullName, user.email, user.phone, roles),
        business = business?.let { BusinessSummaryDto(it.id, it.uniqueCode, it.slug, it.businessName, it.status) },
    )

    companion object {
        fun normalizeEmail(email: String) = email.trim().lowercase()

        fun ensurePasswordFitsBcrypt(password: String, field: String) {
            if (password.toByteArray(Charsets.UTF_8).size > PASSWORD_MAX) {
                throw ApiException.invalidField(field, "La contraseña es demasiado larga")
            }
        }
    }
}
