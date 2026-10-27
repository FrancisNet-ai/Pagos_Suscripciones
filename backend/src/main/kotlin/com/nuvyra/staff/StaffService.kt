package com.nuvyra.staff

import com.nuvyra.auth.AuthService
import com.nuvyra.auth.ClientInfo
import com.nuvyra.businesses.BusinessMember
import com.nuvyra.businesses.BusinessMemberRepository
import com.nuvyra.businesses.BusinessRepository
import com.nuvyra.businesses.BusinessRole
import com.nuvyra.businesses.MemberStatus
import com.nuvyra.businesses.StaffPermission
import com.nuvyra.common.audit.AuditActions
import com.nuvyra.common.audit.AuditService
import com.nuvyra.common.config.AppProperties
import com.nuvyra.common.error.ApiException
import com.nuvyra.common.ratelimit.RateLimiter
import com.nuvyra.common.text.SecureTokens
import com.nuvyra.common.validation.PeruPhone
import com.nuvyra.saas.EntitlementService
import com.nuvyra.security.IssuedSession
import com.nuvyra.security.SessionService
import com.nuvyra.users.User
import com.nuvyra.users.UserRepository
import com.nuvyra.users.UserStatus
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.util.UUID

/**
 * Equipo del negocio. El titular invita colaboradores (BUSINESS_STAFF) con un enlace de un solo uso,
 * decide qué secciones ven y puede revocar el acceso al instante. El número de usuarios respeta el
 * límite del plan SaaS (`admin_user_limit`, titular incluido).
 */
@Service
class StaffService(
    private val members: BusinessMemberRepository,
    private val invitations: StaffInvitationRepository,
    private val users: UserRepository,
    private val businesses: BusinessRepository,
    private val entitlements: EntitlementService,
    private val sessions: SessionService,
    private val notifier: StaffInvitationNotifier,
    private val passwordEncoder: PasswordEncoder,
    private val rateLimiter: RateLimiter,
    private val audit: AuditService,
    private val properties: AppProperties,
    private val jdbc: JdbcTemplate,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun team(businessId: UUID): TeamResponse {
        val list = members.findByBusinessIdOrderByCreatedAtAsc(businessId)
        val people = users.findAllById(list.map { it.userId }).associateBy { it.id }
        return TeamResponse(
            members = list.map { m ->
                val u = people.getValue(m.userId)
                TeamMember(m.id, u.id, u.fullName, u.email, m.role, m.status, m.permissions, m.createdAt!!, m.revokedAt)
            },
            invitations = invitations.findByBusinessIdAndStatusOrderByCreatedAtDesc(businessId, InvitationStatus.PENDING).map(::toPending),
            seatsUsed = seatsUsed(businessId),
            seatLimit = activePlanSeats(businessId),
            permissionOptions = StaffPermission.entries.map { PermissionOption(it, it.label) },
        )
    }

    @Transactional
    fun invite(businessId: UUID, actorId: UUID, request: InviteStaffRequest): InvitationCreated {
        rateLimiter.check("staff-invite:$businessId", limit = 20, window = Duration.ofHours(1))
        val email = AuthService.normalizeEmail(request.email!!)
        if (users.existsByEmail(email)) {
            throw ApiException.invalidField("email", "Este correo ya tiene una cuenta en Nuvyra. Usa otro correo para el colaborador.")
        }
        if (invitations.existsByBusinessIdAndEmailAndStatus(businessId, email, InvitationStatus.PENDING)) {
            throw ApiException.invalidField("email", "Ya hay una invitación pendiente para este correo.")
        }
        lockSeats(businessId)
        ensureSeat(businessId)
        val token = SecureTokens.newToken()
        val invitation = invitations.saveAndFlush(
            StaffInvitation(
                businessId = businessId, email = email, fullName = request.fullName!!.trim(),
                permissionsRaw = StaffPermission.format(request.permissions!!), tokenHash = SecureTokens.sha256Hex(token),
                expiresAt = clock.instant().plus(INVITATION_TTL), invitedBy = actorId,
            ),
        )
        audit.record(
            AuditActions.STAFF_INVITED, actorId = actorId, businessId = businessId, entityType = "STAFF_INVITATION", entityId = invitation.id,
            metadata = mapOf("email" to email, "permissions" to invitation.permissionsRaw),
        )
        val link = link(token)
        val sent = notifier.send(email, invitation.fullName, businesses.findById(businessId).orElseThrow().businessName, link)
        return InvitationCreated(toPending(invitation), link, sent, team(businessId))
    }

    /** Genera un enlace nuevo (el anterior deja de funcionar) y renueva el plazo de 7 días. */
    @Transactional
    fun renewLink(businessId: UUID, actorId: UUID, invitationId: UUID): InvitationCreated {
        val invitation = pending(businessId, invitationId)
        val token = SecureTokens.newToken()
        invitation.tokenHash = SecureTokens.sha256Hex(token)
        invitation.expiresAt = clock.instant().plus(INVITATION_TTL)
        audit.record(AuditActions.STAFF_INVITED, actorId = actorId, businessId = businessId, entityType = "STAFF_INVITATION", entityId = invitation.id, metadata = mapOf("renewed" to true))
        val link = link(token)
        val sent = notifier.send(invitation.email, invitation.fullName, businesses.findById(businessId).orElseThrow().businessName, link)
        return InvitationCreated(toPending(invitation), link, sent, team(businessId))
    }

    @Transactional
    fun cancelInvitation(businessId: UUID, actorId: UUID, invitationId: UUID): TeamResponse {
        val invitation = pending(businessId, invitationId)
        invitation.status = InvitationStatus.CANCELLED
        audit.record(AuditActions.STAFF_INVITATION_CANCELLED, actorId = actorId, businessId = businessId, entityType = "STAFF_INVITATION", entityId = invitation.id)
        return team(businessId)
    }

    @Transactional
    fun updatePermissions(businessId: UUID, actorId: UUID, memberId: UUID, permissions: Set<StaffPermission>): TeamResponse {
        val member = staffMember(businessId, memberId)
        member.permissions = permissions
        audit.record(
            AuditActions.STAFF_PERMISSIONS_UPDATED, actorId = actorId, businessId = businessId, entityType = "BUSINESS_MEMBER", entityId = member.id,
            metadata = mapOf("permissions" to member.permissionsRaw),
        )
        return team(businessId)
    }

    /** Quita el acceso de inmediato: membresía revocada, cuenta desactivada y sesiones cerradas. */
    @Transactional
    fun revoke(businessId: UUID, actorId: UUID, memberId: UUID): TeamResponse {
        val member = staffMember(businessId, memberId)
        if (member.status == MemberStatus.REVOKED) return team(businessId)
        member.status = MemberStatus.REVOKED
        member.revokedAt = clock.instant()
        users.findById(member.userId).ifPresent { it.status = UserStatus.DISABLED }
        sessions.revokeAll(member.userId)
        audit.record(AuditActions.STAFF_REVOKED, actorId = actorId, businessId = businessId, entityType = "BUSINESS_MEMBER", entityId = member.id)
        return team(businessId)
    }

    @Transactional
    fun reactivate(businessId: UUID, actorId: UUID, memberId: UUID): TeamResponse {
        val member = staffMember(businessId, memberId)
        if (member.status == MemberStatus.ACTIVE) return team(businessId)
        lockSeats(businessId)
        ensureSeat(businessId)
        member.status = MemberStatus.ACTIVE
        member.revokedAt = null
        users.findById(member.userId).ifPresent { it.status = UserStatus.ACTIVE }
        audit.record(AuditActions.STAFF_REACTIVATED, actorId = actorId, businessId = businessId, entityType = "BUSINESS_MEMBER", entityId = member.id)
        return team(businessId)
    }

    // ------------------------------------------------------------------ público (enlace de invitación)

    @Transactional(readOnly = true)
    fun view(token: String): PublicInvitation {
        val invitation = usable(token)
        return PublicInvitation(
            businessName = businesses.findById(invitation.businessId).orElseThrow().businessName,
            fullName = invitation.fullName,
            email = invitation.email,
            permissions = invitation.permissions.map { PermissionOption(it, it.label) },
            expiresAt = invitation.expiresAt,
        )
    }

    /** Crea la cuenta del colaborador y su membresía, y abre su sesión. El enlace queda usado. */
    @Transactional
    fun accept(token: String, request: AcceptInvitationRequest, client: ClientInfo): Pair<UUID, IssuedSession> {
        rateLimiter.check("staff-accept:ip:${client.ip}", limit = 10, window = Duration.ofMinutes(15))
        val invitation = invitations.lockById(usable(token).id)!!
        if (invitation.status != InvitationStatus.PENDING) throw invalidLink()
        if (users.existsByEmail(invitation.email)) {
            throw ApiException.conflict("EMAIL_ALREADY_REGISTERED", "Este correo ya tiene una cuenta en Nuvyra. Pide al titular que te invite con otro correo.")
        }
        AuthService.ensurePasswordFitsBcrypt(request.password!!, "password")
        val phone = PeruPhone.normalize(request.phone) ?: throw ApiException.invalidField("phone", "Ingresa un celular peruano válido de 9 dígitos")
        lockSeats(invitation.businessId)
        // La invitación ya ocupa un lugar: se comprueba que el plan siga vigente y admita a todo el equipo.
        val limit = activePlanSeats(invitation.businessId)
            ?: throw ApiException.conflict("PLAN_REQUIRED", "El plan del negocio no está vigente. Pide al titular que lo renueve.")
        if (seatsUsed(invitation.businessId) > limit) {
            throw ApiException.conflict("SEAT_LIMIT_REACHED", "El plan del negocio ya no admite más usuarios. Pide al titular que revise su plan.")
        }
        val now = clock.instant()
        val user = users.save(
            User(
                fullName = request.fullName!!.trim(), email = invitation.email, passwordHash = passwordEncoder.encode(request.password)!!,
                phone = phone, termsAcceptedAt = now, privacyAcceptedAt = now,
            ),
        )
        val member = members.save(BusinessMember(businessId = invitation.businessId, userId = user.id, role = BusinessRole.STAFF).apply { permissions = invitation.permissions })
        invitation.status = InvitationStatus.ACCEPTED
        invitation.acceptedUserId = user.id
        invitation.acceptedAt = now
        audit.record(
            AuditActions.STAFF_JOINED, actorId = user.id, businessId = invitation.businessId, entityType = "BUSINESS_MEMBER", entityId = member.id,
            ipAddress = client.ip,
        )
        return user.id to sessions.create(user.id, client.ip, client.userAgent)
    }

    // ------------------------------------------------------------------ utilidades

    /** Titular y colaboradores activos + invitaciones vigentes. */
    private fun seatsUsed(businessId: UUID): Long =
        members.countByBusinessIdAndStatus(businessId, MemberStatus.ACTIVE) + invitations.countPendingValid(businessId, clock.instant())

    /** Usuarios que admite el plan vigente, o `null` si el negocio no puede operar. */
    private fun activePlanSeats(businessId: UUID): Int? =
        entitlements.access(businessId).takeIf { it.active }?.plan?.adminUserLimit

    private fun ensureSeat(businessId: UUID) {
        val limit = activePlanSeats(businessId)
            ?: throw ApiException.conflict("PLAN_REQUIRED", "Necesitas un plan vigente para sumar colaboradores.")
        if (seatsUsed(businessId) >= limit) {
            throw ApiException.conflict(
                "SEAT_LIMIT_REACHED",
                if (limit == 1) "Tu plan incluye solo al titular. Cambia de plan para sumar colaboradores."
                else "Tu plan admite $limit usuarios (titular incluido) y ya están en uso. Revoca un acceso, cancela una invitación o cambia de plan.",
            )
        }
    }

    /** Serializa altas del mismo negocio para que el límite de usuarios no se supere por concurrencia. */
    private fun lockSeats(businessId: UUID) {
        jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?, 0))", "seats:$businessId")
    }

    private fun staffMember(businessId: UUID, memberId: UUID): BusinessMember {
        val member = members.findByIdAndBusinessId(memberId, businessId) ?: throw ApiException.notFound("El colaborador no existe.")
        if (member.role != BusinessRole.STAFF) throw ApiException(HttpStatus.CONFLICT, "OWNER_IMMUTABLE", "El acceso del titular no se puede modificar.")
        return member
    }

    private fun pending(businessId: UUID, invitationId: UUID): StaffInvitation {
        val invitation = invitations.findByIdAndBusinessId(invitationId, businessId) ?: throw ApiException.notFound("La invitación no existe.")
        if (invitation.status != InvitationStatus.PENDING) throw ApiException.conflict("INVITATION_CLOSED", "Esta invitación ya fue usada o cancelada.")
        return invitation
    }

    private fun usable(token: String): StaffInvitation {
        val invitation = invitations.findByTokenHash(SecureTokens.sha256Hex(token)) ?: throw invalidLink()
        if (invitation.status != InvitationStatus.PENDING || !clock.instant().isBefore(invitation.expiresAt)) throw invalidLink()
        return invitation
    }

    private fun invalidLink() = ApiException(HttpStatus.GONE, "INVITATION_INVALID", "Esta invitación ya no es válida. Pide al titular un enlace nuevo.")

    private fun link(token: String) = "${properties.publicBaseUrl.trimEnd('/')}/invitacion/$token"

    private fun toPending(i: StaffInvitation) = PendingInvitation(
        i.id, i.fullName, i.email, i.permissions, i.expiresAt, !clock.instant().isBefore(i.expiresAt), i.createdAt!!,
    )

    private companion object {
        val INVITATION_TTL: Duration = Duration.ofDays(7)
    }
}
