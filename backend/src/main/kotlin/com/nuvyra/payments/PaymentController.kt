package com.nuvyra.payments

import com.nuvyra.media.ImageType
import com.nuvyra.orders.OrderDetail
import com.nuvyra.orders.OrderService
import com.nuvyra.orders.ReasonRequest
import com.nuvyra.security.CurrentBusiness
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Verificación de pagos")
class PaymentController(
    private val verification: PaymentVerificationService,
    private val orders: OrderService,
) {
    @PostMapping("/{id}/verify")
    @Operation(summary = "Aprueba un pago tras comprobar la recepción real en Yape/Plin")
    fun verify(current: CurrentBusiness, @PathVariable id: UUID): OrderDetail {
        val orderId = verification.verify(current.businessId, current.userId, id)
        return orders.detail(current.businessId, orderId)
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Rechaza un pago reportado indicando el motivo")
    fun reject(current: CurrentBusiness, @PathVariable id: UUID, @Valid @RequestBody body: ReasonRequest): OrderDetail {
        val orderId = verification.reject(current.businessId, current.userId, id, body.reason!!)
        return orders.detail(current.businessId, orderId)
    }

    @GetMapping("/{id}/receipt")
    @Operation(summary = "Descarga el comprobante (solo el vendedor dueño del pago)")
    fun receipt(current: CurrentBusiness, @PathVariable id: UUID): ResponseEntity<Resource> {
        val path = verification.receipt(current.businessId, current.userId, id)
        val type = ImageType.fromExtension(path.fileName.toString().substringAfterLast('.'))!!
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(type.contentType))
            .cacheControl(CacheControl.noStore())
            .header("Content-Disposition", "inline")
            .body(FileSystemResource(path))
    }
}
