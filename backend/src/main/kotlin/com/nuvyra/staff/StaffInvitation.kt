package com.nuvyra.staff

import com.nuvyra.businesses.StaffPermission
import com.nuvyra.common.persistence.VersionedEntity
import com.nuvyra.common.validation.PeruvianMobile
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.LockModeType
import jakarta.persistence.Table
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

enum class InvitationStatus { PENDING, ACCEPTED, CANCELLED }

@Entity
@Table(name = "staff_invitations")
class StaffInvitation(
    @Column(nullable = false, updatable = false) val businessId: UUID,
    @Column(nullable = false, updatable = false) val email: String,
    @Column(nullable = false) var fullName: String,
    @Column(name = "permissions", nullable = false) var permissionsRaw: String,
    @Column(nullable = false) var tokenHash: String,
    @Column(nullable = false) var expiresAt: Instant,
    @Column(nullable = false, updatable = false) val invitedBy: UUID,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var status: InvitationStatus = InvitationStatus.PENDING,
    var acceptedUserId: UUID? = null,
    var acceptedAt: Instant? = null,
) : VersionedEntity() {
    val permissions: Set<StaffPermission> get() = StaffPermission.parse(permissionsRaw)
}

interface StaffInvitationRepository : JpaRepository<StaffInvitation, UUID> {
    fun findByTokenHash(tokenHash: String): StaffInvitation?
    fun findByIdAndBusinessId(id: UUID, businessId: UUID): StaffInvitation?
    fun findByBusinessIdAndStatusOrderByCreatedAtDesc(businessId: UUID, status: InvitationStatus): List<StaffInvitation>
    fun existsByBusinessIdAndEmailAndStatus(businessId: UUID, email: String, status: InvitationStatus): Boolean

    @Query("select count(i) from StaffInvitation i where i.businessId = :businessId and i.status = com.nuvyra.staff.InvitationStatus.PENDING and i.expiresAt > :now")
    fun countPendingValid(businessId: UUID, now: Instant): Long

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from StaffInvitation i where i.id = :id")
    fun lockById(id: UUID): StaffInvitation?
}

data class InviteStaffRequest(
    @field:NotBlank(message = "Ingresa el nombre del colaborador")
    @field:Size(min = 2, max = 120, message = "El nombre debe tener entre 2 y 120 caracteres")
    val fullName: String?,
    @field:NotBlank(message = "Ingresa el correo del colaborador")
    @field:Email(message = "Ingresa un correo electrónico válido")
    @field:Size(max = 254)
    val email: String?,
    @field:NotEmpty(message = "Elige al menos una sección")
    val permissions: Set<StaffPermission>?,
)

data class UpdatePermissionsRequest(
    @field:NotEmpty(message = "Elige al menos una sección")
    val permissions: Set<StaffPermission>?,
)

data class AcceptInvitationRequest(
    @field:NotBlank(message = "Ingresa tu nombre")
    @field:Size(min = 2, max = 120, message = "El nombre debe tener entre 2 y 120 caracteres")
    val fullName: String?,
    @field:NotBlank(message = "Ingresa tu número de celular")
    @field:PeruvianMobile
    val phone: String?,
    @field:NotBlank(message = "Ingresa una contraseña")
    @field:Size(min = 10, max = 72, message = "La contraseña debe tener entre 10 y 72 caracteres")
    val password: String?,
    @field:AssertTrue(message = "Debes aceptar las condiciones de uso")
    val acceptTerms: Boolean?,
    @field:AssertTrue(message = "Debes aceptar la política de privacidad")
    val acceptPrivacy: Boolean?,
)

data class TeamMember(
    val id: UUID,
    val userId: UUID,
    val fullName: String,
    val email: String,
    val role: com.nuvyra.businesses.BusinessRole,
    val status: com.nuvyra.businesses.MemberStatus,
    val permissions: Set<StaffPermission>,
    val since: Instant,
    val revokedAt: Instant?,
)

data class PendingInvitation(
    val id: UUID,
    val fullName: String,
    val email: String,
    val permissions: Set<StaffPermission>,
    val expiresAt: Instant,
    val expired: Boolean,
    val createdAt: Instant,
)

data class PermissionOption(val value: StaffPermission, val label: String)

data class TeamResponse(
    val members: List<TeamMember>,
    val invitations: List<PendingInvitation>,
    /** Usuarios del plan (titular incluido): activos + invitaciones vigentes frente al límite. */
    val seatsUsed: Long,
    val seatLimit: Int?,
    val permissionOptions: List<PermissionOption>,
)

/** El enlace se entrega una sola vez: después solo se guarda su hash. */
data class InvitationCreated(val invitation: PendingInvitation, val link: String, val emailSent: Boolean, val team: TeamResponse)

data class PublicInvitation(
    val businessName: String,
    val fullName: String,
    val email: String,
    val permissions: List<PermissionOption>,
    val expiresAt: Instant,
)
