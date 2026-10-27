package com.platform.payments

import com.platform.common.error.ApiException
import com.platform.security.CurrentBusiness
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/v1/business/me/payment-methods")
@Tag(name = "Métodos de cobro")
class PaymentMethodController(private val service: PaymentMethodService) {

    @GetMapping
    @Operation(summary = "Yape y Plin del negocio (configurados o no)")
    fun list(current: CurrentBusiness): List<PaymentMethodResponse> = service.list(current.businessId)

    @PutMapping("/{method}")
    @Operation(summary = "Guarda el titular declarado, el celular receptor y el estado del método")
    fun update(current: CurrentBusiness, @PathVariable method: String, @Valid @RequestBody body: UpdatePaymentMethodRequest) =
        service.upsert(current.businessId, current.userId, parse(method), body)

    @PostMapping("/{method}/qr", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(summary = "Sube el código QR del método (opcional)")
    fun uploadQr(current: CurrentBusiness, @PathVariable method: String, @RequestPart("file") file: MultipartFile) =
        service.replaceQr(current.businessId, current.userId, parse(method), file)

    @DeleteMapping("/{method}/qr")
    @Operation(summary = "Elimina el código QR del método")
    fun deleteQr(current: CurrentBusiness, @PathVariable method: String) =
        service.replaceQr(current.businessId, current.userId, parse(method), null)

    private fun parse(method: String) =
        PaymentMethodType.entries.firstOrNull { it.name.equals(method, ignoreCase = true) } ?: throw ApiException.notFound("Método de cobro no soportado.")
}
