package com.platform.common.error

import org.springframework.http.HttpStatus

data class FieldErrorDto(val field: String, val message: String)

/** Error de negocio con código estable que el frontend puede interpretar. */
open class ApiException(
    val status: HttpStatus,
    val code: String,
    override val message: String,
    val fieldErrors: List<FieldErrorDto> = emptyList(),
) : RuntimeException(message) {
    companion object {
        fun notFound(message: String = "El recurso solicitado no existe.") =
            ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message)

        fun conflict(code: String, message: String) = ApiException(HttpStatus.CONFLICT, code, message)

        fun forbidden(message: String = "No tienes permiso para realizar esta acción.") =
            ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", message)

        fun unauthorized(code: String = "UNAUTHENTICATED", message: String = "Debes iniciar sesión.") =
            ApiException(HttpStatus.UNAUTHORIZED, code, message)

        fun badRequest(code: String, message: String) = ApiException(HttpStatus.BAD_REQUEST, code, message)

        fun invalidField(field: String, message: String) = ApiException(
            HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Revisa los campos marcados.",
            listOf(FieldErrorDto(field, message)),
        )

        fun rateLimited() = ApiException(
            HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
            "Demasiados intentos. Espera unos minutos antes de volver a intentarlo.",
        )
    }
}
