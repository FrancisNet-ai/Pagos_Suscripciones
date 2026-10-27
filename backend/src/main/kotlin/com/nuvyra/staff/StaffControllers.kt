package com.nuvyra.staff

import com.nuvyra.auth.AuthService
import com.nuvyra.auth.ClientInfo
import com.nuvyra.auth.SessionResponse
import com.nuvyra.common.error.ApiException
import com.nuvyra.common.web.clientIp
import com.nuvyra.security.CurrentBusiness
import com.nuvyra.security.SessionCookies
import com.nuvyra.security.SessionService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Equipo del negocio: solo el titular (Spring Security exige BUSINESS_OWNER en esta ruta). */
@RestController
@RequestMapping("/api/v1/business/me/staff")
@Tag(name = "Equipo")
class StaffController(private val service: StaffService) {

    @GetMapping
    @Operation(summary = "Titular, colaboradores, invitaciones pendientes y usuarios del plan")
    fun team(current: CurrentBusiness) = service.team(current.businessId)

    @PostMapping("/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Invita a un colaborador. Devuelve el enlace una sola vez para compartirlo.")
    fun invite(current: CurrentBusiness, @Valid @RequestBody body: InviteStaffRequest) = service.invite(current.businessId, current.userId, body)

    @PostMapping("/invitations/{id}/renew")
    @Operation(summary = "Genera un enlace nuevo; el anterior deja de funcionar")
    fun renew(current: CurrentBusiness, @PathVariable id: UUID) = service.renewLink(current.businessId, current.userId, id)

    @DeleteMapping("/invitations/{id}")
    fun cancel(current: CurrentBusiness, @PathVariable id: UUID) = service.cancelInvitation(current.businessId, current.userId, id)

    @PutMapping("/{memberId}/permissions")
    fun permissions(current: CurrentBusiness, @PathVariable memberId: UUID, @Valid @RequestBody body: UpdatePermissionsRequest) =
        service.updatePermissions(current.businessId, current.userId, memberId, body.permissions!!)

    @PostMapping("/{memberId}/revoke")
    @Operation(summary = "Quita el acceso de inmediato y cierra sus sesiones")
    fun revoke(current: CurrentBusiness, @PathVariable memberId: UUID) = service.revoke(current.businessId, current.userId, memberId)

    @PostMapping("/{memberId}/reactivate")
    fun reactivate(current: CurrentBusiness, @PathVariable memberId: UUID) = service.reactivate(current.businessId, current.userId, memberId)
}

@RestController
@RequestMapping("/api/v1/public/invitations/{token}")
@Tag(name = "Equipo")
class PublicInvitationController(
    private val service: StaffService,
    private val sessions: SessionService,
    private val auth: AuthService,
    private val cookies: SessionCookies,
) {
    @GetMapping
    fun view(@PathVariable token: String) = service.view(checked(token))

    @PostMapping("/accept")
    @Operation(summary = "Crea la cuenta del colaborador e inicia su sesión")
    fun accept(
        @PathVariable token: String,
        @Valid @RequestBody body: AcceptInvitationRequest,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): SessionResponse {
        val (_, session) = service.accept(checked(token), body, ClientInfo(request.clientIp(), request.getHeader("User-Agent")?.take(255)))
        cookies.write(response, session.token, session.session.absoluteExpiresAt)
        return auth.describe(sessions.authenticate(session.token)!!)
    }

    private fun checked(token: String) = token.takeIf { TOKEN.matches(it) }
        ?: throw ApiException(HttpStatus.GONE, "INVITATION_INVALID", "Esta invitación ya no es válida. Pide al titular un enlace nuevo.")

    private companion object {
        val TOKEN = Regex("^[A-Za-z0-9_-]{43}$")
    }
}
