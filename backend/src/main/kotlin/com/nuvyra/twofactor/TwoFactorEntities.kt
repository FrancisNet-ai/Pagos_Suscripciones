package com.nuvyra.twofactor

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
@Table(name = "user_recovery_codes")
class RecoveryCode(
    @Column(nullable = false, updatable = false) val userId: UUID,
    @Column(nullable = false, updatable = false) val codeHash: String,
    @Column(nullable = false, updatable = false) val createdAt: Instant,
    var usedAt: Instant? = null,
) : AbstractEntity()

interface RecoveryCodeRepository : JpaRepository<RecoveryCode, UUID> {
    fun findByUserIdAndCodeHashAndUsedAtIsNull(userId: UUID, codeHash: String): RecoveryCode?
    fun countByUserIdAndUsedAtIsNull(userId: UUID): Long

    @Modifying
    @Query("delete from RecoveryCode c where c.userId = :userId")
    fun deleteAllForUser(userId: UUID): Int
}

/** Inicio de sesión con la contraseña ya validada, a la espera del segundo factor. */
@Entity
@Table(name = "login_challenges")
class LoginChallenge(
    @Column(nullable = false, updatable = false) val userId: UUID,
    @Column(nullable = false, updatable = false) val tokenHash: String,
    @Column(nullable = false, updatable = false) val createdAt: Instant,
    @Column(nullable = false, updatable = false) val expiresAt: Instant,
    @Column(nullable = false) var attempts: Int = 0,
    var consumedAt: Instant? = null,
) : AbstractEntity()

interface LoginChallengeRepository : JpaRepository<LoginChallenge, UUID> {
    fun findByTokenHash(tokenHash: String): LoginChallenge?

    @Modifying
    @Query("delete from LoginChallenge c where c.expiresAt < :before")
    fun deleteExpiredBefore(before: Instant): Int
}
