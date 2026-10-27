package com.nuvyra.subscriptions

import com.nuvyra.common.web.PageResponse
import com.nuvyra.orders.ReasonRequest
import com.nuvyra.security.CurrentBusiness
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/subscriptions")
@Tag(name = "Suscripciones")
class SubscriptionController(private val service: SubscriptionService) {

    @GetMapping
    @Operation(summary = "Suscripciones del negocio. EXPIRING = activas que vencen dentro de `days` días.")
    fun list(
        current: CurrentBusiness,
        @RequestParam(defaultValue = "ALL") filter: SubscriptionFilter,
        @RequestParam(required = false) query: String?,
        @RequestParam(defaultValue = "7") days: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<SubscriptionResponse> = service.list(current.businessId, filter, query, days, page, size)

    @GetMapping("/expiring")
    @Operation(summary = "Activas que vencen en los próximos `days` días")
    fun expiring(current: CurrentBusiness, @RequestParam(defaultValue = "3") days: Long) =
        service.list(current.businessId, SubscriptionFilter.EXPIRING, null, days, 0, 100)

    @GetMapping("/{id}")
    fun get(current: CurrentBusiness, @PathVariable id: UUID) = service.get(current.businessId, id)

    @PostMapping("/{id}/activate")
    @Operation(summary = "Registra la activación real del servicio (inicio y vencimiento)")
    fun activate(current: CurrentBusiness, @PathVariable id: UUID, @RequestBody(required = false) body: ActivateRequest?) =
        service.activate(current.businessId, current.userId, id, body?.startAt)

    @PostMapping("/{id}/cancel")
    fun cancel(current: CurrentBusiness, @PathVariable id: UUID, @Valid @RequestBody body: ReasonRequest) =
        service.cancel(current.businessId, current.userId, id, body.reason!!)

    @PostMapping("/{id}/release-capacity")
    @Operation(summary = "Confirma que el acceso fue retirado y libera el cupo")
    fun releaseCapacity(current: CurrentBusiness, @PathVariable id: UUID) =
        service.confirmAccessRemoved(current.businessId, current.userId, id)
}
