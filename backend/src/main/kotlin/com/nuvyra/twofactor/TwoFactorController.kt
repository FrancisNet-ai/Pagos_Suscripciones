package com.nuvyra.twofactor

import com.nuvyra.auth.ClientInfo
import com.nuvyra.common.web.clientIp
import com.nuvyra.common.web.userAgentTruncated
import com.nuvyra.security.AuthenticatedUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

data class PasswordConfirmationRequest(
    @field:NotBlank(message = "Ingresa tu contraseña")
    @field:Size(max = 200)
    val password: String?,
)

data class TwoFactorCodeRequest(
    @field:NotBlank(message = "Ingresa el código")
    @field:Size(max = 20)
    val code: String?,
)

data class DisableTwoFactorRequest(
    @field:NotBlank(message = "Ingresa tu contraseña")
    @field:Size(max = 200)
    val password: String?,

    @field:NotBlank(message = "Ingresa un código de tu app o de recuperación")
    @field:Size(max = 20)
    val code: String?,
)

@RestController
@RequestMapping("/api/v1/auth/2fa")
@Tag(name = "Verificación en dos pasos")
class TwoFactorController(private val service: TwoFactorService) {

    @GetMapping
    @Operation(summary = "Estado de la verificación en dos pasos de la cuenta")
    fun status(user: AuthenticatedUser): TwoFactorStatusResponse = service.status(user)

    @PostMapping("/setup")
    @Operation(summary = "Genera el secreto y el QR para la app de autenticación (exige la contraseña)")
    fun setup(user: AuthenticatedUser, @Valid @RequestBody body: PasswordConfirmationRequest): TwoFactorSetupResponse =
        service.startSetup(user, body.password!!)

    @PostMapping("/enable")
    @Operation(summary = "Activa la verificación con el primer código; devuelve los códigos de recuperación")
    fun enable(user: AuthenticatedUser, @Valid @RequestBody body: TwoFactorCodeRequest, request: HttpServletRequest): TwoFactorEnabledResponse =
        service.enable(user, body.code!!, request.client())

    @PostMapping("/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Desactiva la verificación (exige contraseña y un código)")
    fun disable(user: AuthenticatedUser, @Valid @RequestBody body: DisableTwoFactorRequest, request: HttpServletRequest) {
        service.disable(user, body.password!!, body.code!!, request.client())
    }

    @PostMapping("/recovery-codes")
    @Operation(summary = "Genera códigos de recuperación nuevos e invalida los anteriores")
    fun regenerate(user: AuthenticatedUser, @Valid @RequestBody body: TwoFactorCodeRequest, request: HttpServletRequest): RecoveryCodesResponse =
        service.regenerateRecoveryCodes(user, body.code!!, request.client())

    private fun HttpServletRequest.client() = ClientInfo(clientIp(), userAgentTruncated())
}
