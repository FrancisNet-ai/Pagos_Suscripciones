package com.nuvyra.security

import com.nuvyra.businesses.BusinessMemberRepository
import com.nuvyra.businesses.MemberStatus
import com.nuvyra.common.config.AppProperties
import com.nuvyra.common.text.SecureTokens
import com.nuvyra.users.PlatformRole
import com.nuvyra.users.UserRepository
import com.nuvyra.users.UserStatus
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class IssuedSession(val token: String, val session: UserSession)

@Service
class SessionService(
    private val sessions: UserSessionRepository,
    private val users: UserRepository,
    private val members: BusinessMemberRepository,
    private val properties: AppProperties,
    private val clock: Clock,
) {
    private val touchInterval = Duration.ofMinutes(1)

    @Transactional
    fun create(userId: UUID, ipAddress: String?, userAgent: String?): IssuedSession {
        val now = clock.instant()
        val token = SecureTokens.newToken()
        val session = sessions.save(
            UserSession(
                userId = userId,
                tokenHash = SecureTokens.sha256Hex(token),
                createdAt = now,
                lastUsedAt = now,
                idleExpiresAt = minOf(now.plus(properties.session.idleTimeout), now.plus(properties.session.absoluteTimeout)),
                absoluteExpiresAt = now.plus(properties.session.absoluteTimeout),
                ipAddress = ipAddress,
                userAgent = userAgent,
            ),
        )
        return IssuedSession(token, session)
    }

    /** Cambia el token de una sesión activa (mitiga la reutilización de tokens filtrados). */
    @Transactional
    fun rotate(sessionId: UUID): IssuedSession? {
        val now = clock.instant()
        val session = sessions.findById(sessionId).orElse(null)?.takeIf { it.isActive(now) } ?: return null
        val token = SecureTokens.newToken()
        session.tokenHash = SecureTokens.sha256Hex(token)
        session.lastUsedAt = now
        session.idleExpiresAt = minOf(now.plus(properties.session.idleTimeout), session.absoluteExpiresAt)
        return IssuedSession(token, session)
    }

    /** Cambia el negocio activo de la sesión; solo a uno en el que la persona sea miembro activo. */
    @Transactional
    fun switchBusiness(sessionId: UUID, userId: UUID, businessId: UUID): Boolean {
        members.findByBusinessIdAndUserIdAndStatus(businessId, userId, MemberStatus.ACTIVE) ?: return false
        val session = sessions.findById(sessionId).orElse(null)?.takeIf { it.userId == userId && it.isActive(clock.instant()) } ?: return false
        session.activeBusinessId = businessId
        return true
    }

    @Transactional
    fun revoke(sessionId: UUID) {
        sessions.findById(sessionId).ifPresent { if (it.revokedAt == null) it.revokedAt = clock.instant() }
    }

    /** Cierra todas las sesiones de la persona salvo la actual. Devuelve cuántas cerró. */
    @Transactional
    fun revokeOthers(userId: UUID, keepSessionId: UUID): Int = sessions.revokeOthersForUser(userId, keepSessionId, clock.instant())

    /** Sesiones vigentes de la persona, la más reciente primero. */
    @Transactional(readOnly = true)
    fun active(userId: UUID): List<UserSession> {
        val now = clock.instant()
        return sessions.findByUserIdAndRevokedAtIsNullOrderByLastUsedAtDesc(userId).filter { it.isActive(now) }
    }

    /** Cierra una sesión propia. Devuelve `false` si no existe, es de otra persona o ya estaba cerrada. */
    @Transactional
    fun revokeOwn(userId: UUID, sessionId: UUID): Boolean {
        val session = sessions.findById(sessionId).orElse(null)?.takeIf { it.userId == userId && it.revokedAt == null } ?: return false
        session.revokedAt = clock.instant()
        return true
    }

    @Transactional
    fun revokeAll(userId: UUID) {
        sessions.revokeAllForUser(userId, clock.instant())
    }

    /** Valida el token de la cookie y construye el principal. Devuelve `null` si no es válido. */
    @Transactional
    fun authenticate(token: String): AuthenticatedUser? {
        val now = clock.instant()
        val session = sessions.findByTokenHash(SecureTokens.sha256Hex(token))?.takeIf { it.isActive(now) } ?: return null
        return principal(session, now)
    }

    /** Principal de una sesión ya identificada (p. ej. tras cambiar de negocio en la misma petición). */
    @Transactional
    fun principal(sessionId: UUID): AuthenticatedUser? {
        val now = clock.instant()
        val session = sessions.findById(sessionId).orElse(null)?.takeIf { it.isActive(now) } ?: return null
        return principal(session, now)
    }

    private fun principal(session: UserSession, now: Instant): AuthenticatedUser? {
        val user = users.findById(session.userId).orElse(null)?.takeIf { it.status == UserStatus.ACTIVE } ?: return null
        // El negocio elegido en la sesión, si la membresía sigue activa; si no, el más antiguo.
        val membership = session.activeBusinessId?.let { members.findByBusinessIdAndUserIdAndStatus(it, user.id, MemberStatus.ACTIVE) }
            ?: members.findFirstByUserIdAndStatusOrderByCreatedAtAsc(user.id, MemberStatus.ACTIVE)

        val roles = buildSet {
            if (user.platformRole == PlatformRole.SUPER_ADMIN) add(Roles.SUPER_ADMIN)
            membership?.let { add(Roles.forBusinessRole(it.role)) }
        }
        if (Duration.between(session.lastUsedAt, now) > touchInterval) {
            sessions.touch(session.id, now, minOf(now.plus(properties.session.idleTimeout), session.absoluteExpiresAt))
        }
        return AuthenticatedUser(
            userId = user.id,
            sessionId = session.id,
            email = user.email,
            roles = roles,
            businessId = membership?.businessId,
            businessRole = membership?.role,
            permissions = membership?.permissions.orEmpty(),
            twoFactorEnabled = user.twoFactorEnabled,
        )
    }

    @Scheduled(cron = "0 17 3 * * *")
    fun purgeExpired() {
        sessions.deleteExpiredBefore(clock.instant().minus(Duration.ofDays(7)))
    }

    private fun minOf(a: Instant, b: Instant): Instant = if (a.isBefore(b)) a else b
}
