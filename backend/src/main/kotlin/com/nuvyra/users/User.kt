package com.nuvyra.users

import com.nuvyra.common.persistence.VersionedEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

enum class PlatformRole { SUPER_ADMIN }

enum class UserStatus { ACTIVE, DISABLED }

@Entity
@Table(name = "users")
class User(
    @Column(nullable = false) var fullName: String,
    @Column(nullable = false) var email: String,
    @Column(nullable = false) var passwordHash: String,
    @Column(nullable = false) var phone: String,
    @Enumerated(EnumType.STRING) var platformRole: PlatformRole? = null,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var status: UserStatus = UserStatus.ACTIVE,
    var termsAcceptedAt: Instant? = null,
    var privacyAcceptedAt: Instant? = null,
    /** Secreto TOTP cifrado (AES-GCM); solo existe con la verificación en dos pasos activa. */
    var totpSecretEncrypted: String? = null,
    /** Secreto generado en la configuración, a la espera de confirmar el primer código. */
    var totpPendingSecretEncrypted: String? = null,
    var totpEnabledAt: Instant? = null,
    var totpLastStep: Long? = null,
) : VersionedEntity() {
    val twoFactorEnabled: Boolean get() = totpEnabledAt != null && totpSecretEncrypted != null
}

interface UserRepository : JpaRepository<User, UUID> {
    fun findByEmail(email: String): User?
    fun existsByEmail(email: String): Boolean
}
