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

    @Transactional
    fun revoke(sessionId: UUID) {
        sessions.findById(sessionId).ifPresent { if (it.revokedAt == null) it.revokedAt = clock.instant() }
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
        val user = users.findById(session.userId).orElse(null)?.takeIf { it.status == UserStatus.ACTIVE } ?: return null
        val membership = members.findFirstByUserIdAndStatusOrderByCreatedAtAsc(user.id, MemberStatus.ACTIVE)

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
        )
    }

    @Scheduled(cron = "0 17 3 * * *")
    fun purgeExpired() {
        sessions.deleteExpiredBefore(clock.instant().minus(Duration.ofDays(7)))
    }

    private fun minOf(a: Instant, b: Instant): Instant = if (a.isBefore(b)) a else b
}
