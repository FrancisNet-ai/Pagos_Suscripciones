package com.platform.security

import com.platform.common.persistence.AbstractEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "user_sessions")
class UserSession(
    @Column(nullable = false, updatable = false) val userId: UUID,
    @Column(nullable = false) var tokenHash: String,
    @Column(nullable = false, updatable = false) val createdAt: Instant,
    @Column(nullable = false) var lastUsedAt: Instant,
    @Column(nullable = false) var idleExpiresAt: Instant,
    @Column(nullable = false, updatable = false) val absoluteExpiresAt: Instant,
    var revokedAt: Instant? = null,
    @Column(updatable = false) val ipAddress: String? = null,
    @Column(updatable = false) val userAgent: String? = null,
) : AbstractEntity() {
    fun isActive(now: Instant) = revokedAt == null && now.isBefore(idleExpiresAt) && now.isBefore(absoluteExpiresAt)
}

interface UserSessionRepository : JpaRepository<UserSession, UUID> {
    fun findByTokenHash(tokenHash: String): UserSession?

    @Transactional
    @Modifying
    @Query("update UserSession s set s.lastUsedAt = :now, s.idleExpiresAt = :idleExpiresAt where s.id = :id and s.revokedAt is null")
    fun touch(id: UUID, now: Instant, idleExpiresAt: Instant): Int

    @Transactional
    @Modifying
    @Query("update UserSession s set s.revokedAt = :now where s.userId = :userId and s.revokedAt is null")
    fun revokeAllForUser(userId: UUID, now: Instant): Int

    @Transactional
    @Modifying
    @Query("delete from UserSession s where s.absoluteExpiresAt < :before or s.revokedAt < :before")
    fun deleteExpiredBefore(before: Instant): Int
}
