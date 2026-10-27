package com.nuvyra.saas

import com.nuvyra.security.CurrentBusiness
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
@RequestMapping("/api/v1/public/saas-plans")
@Tag(name = "Planes SaaS (público)")
class PublicPlanController(private val plans: PlanService) {
    @GetMapping
    @Operation(summary = "Planes activos para la página comercial y el registro")
    fun list() = plans.publicPlans()
}

/**
 * Plan del propio vendedor. Siempre disponible, aunque el plan haya vencido o el negocio esté
 * suspendido: es la vía para pagar, renovar y contactar a soporte.
 */
@RestController
@RequestMapping("/api/v1/billing")
@Tag(name = "Mi plan")
class BillingController(private val billing: BillingService) {

    @GetMapping
    @Operation(summary = "Plan vigente, uso, cobro abierto, instrucciones de pago e historial")
    fun overview(current: CurrentBusiness) = billing.overview(current.businessId)

    @PostMapping("/checkout")
    @Operation(summary = "Elige un plan: genera el cobro (o activa al instante un plan gratuito)")
    fun checkout(current: CurrentBusiness, @Valid @RequestBody body: CheckoutRequest) =
        billing.checkout(current.businessId, current.userId, body.planId!!)

    @PostMapping("/invoices/{id}/payments", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(summary = "Reporta el pago del cobro. No activa el plan: lo verifica Nuvyra.")
    fun report(
        current: CurrentBusiness,
        @PathVariable id: UUID,
        @Valid fields: SaasPaymentFields,
        @RequestPart("receipt", required = false) receipt: MultipartFile?,
    ) = billing.reportPayment(
        current.businessId, current.userId, id, fields.method!!, fields.transactionReference!!, receipt?.takeIf { !it.isEmpty }?.bytes,
    )

    @PostMapping("/invoices/{id}/cancel")
    @Operation(summary = "Anula un cobro abierto sin pagos en revisión")
    fun cancel(current: CurrentBusiness, @PathVariable id: UUID) = billing.cancelInvoice(current.businessId, current.userId, id)
}
