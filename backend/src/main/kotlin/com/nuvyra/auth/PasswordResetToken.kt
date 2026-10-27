package com.nuvyra.auth

import com.nuvyra.common.persistence.AbstractEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "password_reset_tokens")
class PasswordResetToken(
    @Column(nullable = false, updatable = false) val userId: UUID,
    @Column(nullable = false, updatable = false) val tokenHash: String,
    @Column(nullable = false, updatable = false) val expiresAt: Instant,
    @Column(nullable = false, updatable = false) val createdAt: Instant,
    var usedAt: Instant? = null,
) : AbstractEntity()

interface PasswordResetTokenRepository : JpaRepository<PasswordResetToken, UUID> {
    fun findByTokenHash(tokenHash: String): PasswordResetToken?

    /** Invalida enlaces anteriores al emitir uno nuevo. */
    @Modifying
    @Query("update PasswordResetToken t set t.usedAt = :now where t.userId = :userId and t.usedAt is null")
    fun invalidateAllForUser(userId: UUID, now: Instant): Int
}
