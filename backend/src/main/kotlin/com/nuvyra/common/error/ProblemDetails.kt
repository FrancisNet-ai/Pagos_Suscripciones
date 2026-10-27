package com.nuvyra.common.error

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail

object ProblemDetails {
    fun of(status: HttpStatus, code: String, detail: String, errors: List<FieldErrorDto> = emptyList()): ProblemDetail =
        ProblemDetail.forStatusAndDetail(status, detail).apply {
            title = titleFor(status)
            setProperty("code", code)
            if (errors.isNotEmpty()) setProperty("errors", errors)
        }

    private fun titleFor(status: HttpStatus) = when (status) {
        HttpStatus.BAD_REQUEST -> "Datos inválidos"
        HttpStatus.UNAUTHORIZED -> "No autenticado"
        HttpStatus.FORBIDDEN -> "Acceso denegado"
        HttpStatus.NOT_FOUND -> "No encontrado"
        HttpStatus.CONFLICT -> "Conflicto"
        HttpStatus.TOO_MANY_REQUESTS -> "Demasiadas solicitudes"
        HttpStatus.PAYLOAD_TOO_LARGE -> "Archivo demasiado grande"
        else -> if (status.is5xxServerError) "Error interno" else status.reasonPhrase
    }
}
