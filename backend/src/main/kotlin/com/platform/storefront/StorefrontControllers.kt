package com.platform.storefront

import com.platform.common.error.ApiException
import com.platform.common.web.clientIp
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
@RequestMapping("/api/v1/public/stores/{slug}")
@Tag(name = "Tienda pública")
class PublicStoreController(
    private val storefront: StorefrontService,
    private val checkout: CheckoutService,
) {
    @GetMapping
    @Operation(summary = "Datos públicos de la tienda")
    fun store(@PathVariable slug: String) = storefront.store(slug.take(60))

    @GetMapping("/products")
    @Operation(summary = "Productos publicados de la tienda")
    fun products(@PathVariable slug: String) = storefront.products(slug.take(60))

    @GetMapping("/products/{productId}")
    fun product(@PathVariable slug: String, @PathVariable productId: UUID) = storefront.product(slug.take(60), productId)

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea un pedido sin registro. El precio lo determina el servidor.")
    fun createOrder(
        @PathVariable slug: String,
        @Valid @RequestBody body: CreateOrderRequest,
        @RequestHeader("Idempotency-Key", required = false) idempotencyKey: UUID?,
        request: HttpServletRequest,
    ) = checkout.createOrder(slug.take(60), body, idempotencyKey, request.clientIp())
}

@RestController
@RequestMapping("/api/v1/public/orders/{token}")
@Tag(name = "Pedido del comprador")
class PublicOrderController(private val service: PublicOrderService) {

    @GetMapping
    @Operation(summary = "Estado real del pedido")
    fun view(@PathVariable token: String) = service.view(checked(token))

    @PostMapping("/payment-report", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(summary = "Reporta el código de operación y, opcionalmente, el comprobante. No verifica el pago.")
    fun report(
        @PathVariable token: String,
        @Valid fields: ReportPaymentFields,
        @RequestPart("receipt", required = false) receipt: MultipartFile?,
        request: HttpServletRequest,
    ) = service.reportPayment(checked(token), fields.transactionReference!!, receipt?.takeIf { !it.isEmpty }?.bytes, request.clientIp())

    private fun checked(token: String) = token.takeIf { TOKEN.matches(it) } ?: throw ApiException.notFound("No encontramos este pedido.")

    companion object {
        private val TOKEN = Regex("^[A-Za-z0-9_-]{43}$")
    }
}
