package com.platform.orders

import com.platform.businesses.BusinessService
import com.platform.common.web.PageResponse
import com.platform.security.CurrentBusiness
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class OrdersSummary(val toVerify: Long)

@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Ventas")
class OrderController(private val service: OrderService, private val businesses: BusinessService) {

    @GetMapping
    @Operation(summary = "Ventas del negocio con filtros por estado, fecha, producto y búsqueda")
    fun list(
        current: CurrentBusiness,
        @RequestParam(defaultValue = "ALL") filter: OrderFilter,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
        @RequestParam(required = false) productId: UUID?,
        @RequestParam(required = false) query: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<OrderSummary> {
        val zone = ZoneId.of(businesses.get(current.businessId).timeZone)
        return service.list(current.businessId, zone, filter, from, to, productId, query, page, size)
    }

    @GetMapping("/summary")
    fun summary(current: CurrentBusiness) = OrdersSummary(service.countToVerify(current.businessId))

    @GetMapping("/{id}")
    fun detail(current: CurrentBusiness, @PathVariable id: UUID) = service.detail(current.businessId, id)

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancela un pedido sin pago verificado y libera su reserva")
    fun cancel(current: CurrentBusiness, @PathVariable id: UUID, @Valid @RequestBody body: ReasonRequest) =
        service.cancel(current.businessId, current.userId, id, body.reason!!)
}
