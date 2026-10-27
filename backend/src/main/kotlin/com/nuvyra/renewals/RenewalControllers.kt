package com.nuvyra.renewals

import com.nuvyra.common.error.ApiException
import com.nuvyra.common.web.PageResponse
import com.nuvyra.common.web.clientIp
import com.nuvyra.security.CurrentBusiness
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/public/renewals/{token}")
@Tag(name = "Renovación (enlace público)")
class PublicRenewalController(private val service: RenewalService) {

    @GetMapping
    @Operation(summary = "Datos mínimos de la renovación: producto, negocio, vencimiento y precio vigente")
    fun view(@PathVariable token: String) = service.view(checked(token))

    @PostMapping("/response")
    @Operation(summary = "Respuesta del cliente. «Sí» registra la intención; NO renueva sin pago verificado.")
    fun respond(@PathVariable token: String, @Valid @RequestBody body: RenewalResponseRequest, request: HttpServletRequest) =
        service.respond(checked(token), body.response!!, request.clientIp())

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea el pedido de renovación (precio vigente, mismo negocio y producto)")
    fun createOrder(@PathVariable token: String, @Valid @RequestBody body: RenewalOrderRequest, request: HttpServletRequest) =
        service.createOrder(checked(token), body, request.clientIp())

    @PostMapping("/opt-out")
    @Operation(summary = "El cliente deja de recibir avisos de este negocio")
    fun optOut(@PathVariable token: String, request: HttpServletRequest) = service.optOut(checked(token), request.clientIp())

    private fun checked(token: String) = token.takeIf { TOKEN.matches(it) } ?: throw ApiException.notFound("Este enlace de renovación no es válido.")

    companion object {
        private val TOKEN = Regex("^[A-Za-z0-9_-]{43}$")
    }
}

data class OptOutRequest(val optedOut: Boolean)

@RestController
@RequestMapping("/api/v1/renewals")
@Tag(name = "Renovaciones")
class RenewalController(private val service: RenewalService) {

    @GetMapping
    @Operation(summary = "Solicitudes por grupo: UPCOMING, ACCEPTED, DECLINED, NO_RESPONSE, COMPLETED")
    fun list(
        current: CurrentBusiness,
        @RequestParam(defaultValue = "UPCOMING") bucket: RenewalBucket,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<RenewalSummary> = service.list(current.businessId, bucket, page, size)

    @GetMapping("/overview")
    fun overview(current: CurrentBusiness) = service.overview(current.businessId)

    @GetMapping("/notifications")
    @Operation(summary = "Avisos listos para enviar por WhatsApp (envío manual del vendedor)")
    fun notifications(current: CurrentBusiness, @RequestParam(defaultValue = "0") page: Int, @RequestParam(defaultValue = "50") size: Int) =
        service.dueNotifications(current.businessId, page, size)

    @PostMapping("/notifications/{id}/sent")
    @Operation(summary = "El vendedor confirma que envió el aviso desde su WhatsApp")
    fun markSent(current: CurrentBusiness, @PathVariable id: UUID) = service.markSent(current.businessId, current.userId, id)

    @PostMapping("/notifications/{id}/skip")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun skip(current: CurrentBusiness, @PathVariable id: UUID) = service.skip(current.businessId, id)

    @GetMapping("/settings")
    fun settings(current: CurrentBusiness) = service.settings(current.businessId)

    @PutMapping("/settings")
    @Operation(summary = "Días de aviso previo, aviso el día del vencimiento y plazo de gracia")
    fun updateSettings(current: CurrentBusiness, @Valid @RequestBody body: RenewalSettingsRequest) =
        service.updateSettings(current.businessId, current.userId, body)
}
