package com.platform.common.error

import org.slf4j.LoggerFactory
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ApiException::class)
    fun handleApi(ex: ApiException): ProblemDetail = ProblemDetails.of(ex.status, ex.code, ex.message, ex.fieldErrors)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ProblemDetail {
        val errors = ex.bindingResult.fieldErrors.map { FieldErrorDto(it.field, it.defaultMessage ?: "Valor inválido") } +
            ex.bindingResult.globalErrors.map { FieldErrorDto(it.objectName, it.defaultMessage ?: "Valor inválido") }
        return validationProblem(errors)
    }

    @ExceptionHandler(HandlerMethodValidationException::class)
    fun handleMethodValidation(ex: HandlerMethodValidationException): ProblemDetail {
        val errors = ex.parameterValidationResults.flatMap { result ->
            result.resolvableErrors.map { FieldErrorDto(result.methodParameter.parameterName ?: "param", it.defaultMessage ?: "Valor inválido") }
        }
        return validationProblem(errors)
    }

    @ExceptionHandler(
        HttpMessageNotReadableException::class,
        MethodArgumentTypeMismatchException::class,
        MissingServletRequestParameterException::class,
    )
    fun handleUnreadable(ex: Exception): ProblemDetail =
        ProblemDetails.of(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "La solicitud no tiene un formato válido.")

    @ExceptionHandler(OptimisticLockingFailureException::class)
    fun handleOptimisticLock(ex: OptimisticLockingFailureException): ProblemDetail =
        ProblemDetails.of(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION", "El registro fue modificado por otra operación. Recarga e inténtalo de nuevo.")

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleUploadSize(ex: MaxUploadSizeExceededException): ProblemDetail =
        ProblemDetails.of(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "El archivo supera el tamaño permitido.")

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethod(ex: HttpRequestMethodNotSupportedException): ProblemDetail =
        ProblemDetails.of(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "Método no permitido.")

    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun handleMediaType(ex: HttpMediaTypeNotSupportedException): ProblemDetail =
        ProblemDetails.of(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Tipo de contenido no soportado.")

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResource(ex: NoResourceFoundException): ProblemDetail =
        ProblemDetails.of(HttpStatus.NOT_FOUND, "NOT_FOUND", "El recurso solicitado no existe.")

    // Se relanzan para que los gestione Spring Security (401/403 consistentes).
    @ExceptionHandler(AccessDeniedException::class, AuthenticationException::class)
    fun rethrowSecurity(ex: RuntimeException): Nothing = throw ex

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ProblemDetail {
        log.error("Error no controlado", ex)
        return ProblemDetails.of(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Ocurrió un error inesperado. Inténtalo nuevamente.")
    }

    private fun validationProblem(errors: List<FieldErrorDto>) =
        ProblemDetails.of(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Revisa los campos marcados.", errors)
}
