package com.nuvyra.auth

import com.nuvyra.common.web.clientIp
import com.nuvyra.common.web.userAgentTruncated
import com.nuvyra.security.AuthenticatedUser
import com.nuvyra.security.SessionCookies
import com.nuvyra.security.SessionService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticación")
class AuthController(
    private val authService: AuthService,
    private val passwordResetService: PasswordResetService,
    private val sessionService: SessionService,
    private val cookies: SessionCookies,
) {

    @GetMapping("/csrf")
    @Operation(summary = "Emite la cookie XSRF-TOKEN")
    fun csrf(token: CsrfToken): ResponseEntity<Void> {
        token.token // fuerza la generación y escritura de la cookie
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra un vendedor y su negocio, e inicia sesión")
    fun register(@Valid @RequestBody body: RegisterRequest, request: HttpServletRequest, response: HttpServletResponse): SessionResponse {
        val result = authService.register(body, request.client())
        cookies.write(response, result.session.token, result.session.session.absoluteExpiresAt)
        return result.response
    }

    @PostMapping("/login")
    @Operation(summary = "Inicia sesión con correo y contraseña")
    fun login(@Valid @RequestBody body: LoginRequest, request: HttpServletRequest, response: HttpServletResponse): SessionResponse {
        val result = authService.login(body, request.client())
        cookies.write(response, result.session.token, result.session.session.absoluteExpiresAt)
        return result.response
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rota el token de la sesión actual")
    fun refresh(user: AuthenticatedUser, response: HttpServletResponse): SessionResponse {
        val rotated = sessionService.rotate(user.sessionId) ?: throw com.nuvyra.common.error.ApiException.unauthorized()
        cookies.write(response, rotated.token, rotated.session.absoluteExpiresAt)
        return authService.describe(user)
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cierra la sesión actual")
    fun logout(user: AuthenticatedUser, request: HttpServletRequest, response: HttpServletResponse) {
        authService.logout(user, request.client())
        cookies.clear(response)
    }

    @PostMapping("/password")
    @Operation(summary = "Cambia la contraseña (exige la actual) y cierra las demás sesiones")
    fun changePassword(user: AuthenticatedUser, @Valid @RequestBody body: ChangePasswordRequest, request: HttpServletRequest): ChangePasswordResponse =
        authService.changePassword(user, body, request.client())

    @PostMapping("/business")
    @Operation(summary = "Cambia el negocio activo de la sesión")
    fun switchBusiness(user: AuthenticatedUser, @Valid @RequestBody body: SwitchBusinessRequest): SessionResponse =
        authService.switchBusiness(user, body.businessId!!)

    @PostMapping("/businesses")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea otro negocio para la cuenta actual y lo activa")
    fun createBusiness(user: AuthenticatedUser, @Valid @RequestBody body: CreateBusinessRequest, request: HttpServletRequest): SessionResponse =
        authService.createBusiness(user, body, request.client())

    @GetMapping("/me")
    @Operation(summary = "Usuario, roles y negocio de la sesión actual")
    fun me(user: AuthenticatedUser): SessionResponse = authService.describe(user)

    @PostMapping("/password-reset")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Solicita un enlace de recuperación (respuesta idéntica exista o no la cuenta)")
    fun requestPasswordReset(@Valid @RequestBody body: PasswordResetRequest, request: HttpServletRequest) {
        passwordResetService.request(body.email!!, request.clientIp())
    }

    @PostMapping("/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Establece una nueva contraseña con el token recibido")
    fun confirmPasswordReset(@Valid @RequestBody body: PasswordResetConfirmRequest, request: HttpServletRequest) {
        passwordResetService.confirm(body.token!!, body.newPassword!!, request.clientIp())
    }

    private fun HttpServletRequest.client() = ClientInfo(clientIp(), userAgentTruncated())
}
