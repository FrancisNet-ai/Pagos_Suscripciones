package com.platform.auth

import com.platform.businesses.BusinessStatus
import com.platform.common.validation.PeruvianMobile
import com.platform.saas.AccessSummary
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

const val PASSWORD_MIN = 10
const val PASSWORD_MAX = 72 // límite de BCrypt en bytes; se valida también en el servicio

data class RegisterRequest(
    @field:NotBlank(message = "Ingresa tu nombre")
    @field:Size(min = 2, max = 120, message = "El nombre debe tener entre 2 y 120 caracteres")
    val fullName: String?,

    @field:NotBlank(message = "Ingresa tu correo electrónico")
    @field:Email(message = "Ingresa un correo electrónico válido")
    @field:Size(max = 254, message = "El correo es demasiado largo")
    val email: String?,

    @field:NotBlank(message = "Ingresa una contraseña")
    @field:Size(min = PASSWORD_MIN, max = PASSWORD_MAX, message = "La contraseña debe tener entre 10 y 72 caracteres")
    val password: String?,

    @field:NotBlank(message = "Ingresa tu número de celular")
    @field:PeruvianMobile
    val phone: String?,

    @field:NotBlank(message = "Ingresa el nombre de tu negocio")
    @field:Size(min = 2, max = 120, message = "El nombre del negocio debe tener entre 2 y 120 caracteres")
    val businessName: String?,

    @field:AssertTrue(message = "Debes aceptar las condiciones comerciales")
    val acceptTerms: Boolean?,

    @field:AssertTrue(message = "Debes aceptar la política de privacidad")
    val acceptPrivacy: Boolean?,

    /** Plan SaaS elegido al registrarse (opcional: también puede elegirse después en «Mi plan»). */
    val planId: UUID? = null,
)

data class LoginRequest(
    @field:NotBlank(message = "Ingresa tu correo electrónico")
    @field:Size(max = 254)
    val email: String?,

    @field:NotBlank(message = "Ingresa tu contraseña")
    @field:Size(max = 200)
    val password: String?,
)

data class PasswordResetRequest(
    @field:NotBlank(message = "Ingresa tu correo electrónico")
    @field:Email(message = "Ingresa un correo electrónico válido")
    @field:Size(max = 254)
    val email: String?,
)

data class PasswordResetConfirmRequest(
    @field:NotBlank(message = "El enlace no es válido")
    @field:Size(max = 128)
    val token: String?,

    @field:NotBlank(message = "Ingresa una contraseña")
    @field:Size(min = PASSWORD_MIN, max = PASSWORD_MAX, message = "La contraseña debe tener entre 10 y 72 caracteres")
    val newPassword: String?,
)

data class UserDto(
    val id: UUID,
    val fullName: String,
    val email: String,
    val phone: String,
    val roles: Set<String>,
)

data class BusinessSummaryDto(
    val id: UUID,
    val uniqueCode: String,
    val slug: String,
    val businessName: String,
    val status: BusinessStatus,
    /** Si el plan permite usar las funciones comerciales y qué funciones incluye. */
    val access: AccessSummary,
)

data class SessionResponse(
    val user: UserDto,
    val business: BusinessSummaryDto?,
)
