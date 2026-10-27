package com.nuvyra.admin

import com.nuvyra.common.error.ApiException
import com.nuvyra.media.ImageType
import com.nuvyra.orders.ReasonRequest
import com.nuvyra.payments.PaymentMethodType
import com.nuvyra.saas.BillingService
import com.nuvyra.saas.PlanRequest
import com.nuvyra.saas.PlanService
import com.nuvyra.saas.PlatformPaymentMethodRequest
import com.nuvyra.saas.PlatformSettingsRequest
import com.nuvyra.saas.SaasPaymentStatus
import com.nuvyra.security.AuthenticatedUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

/** Superadministración. Spring Security exige el rol SUPER_ADMIN para todas las rutas `/api/v1/admin`. */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Superadministración")
class AdminController(
    private val admin: AdminService,
    private val plans: PlanService,
    private val billing: BillingService,
) {
    @GetMapping("/overview")
    @Operation(summary = "Indicadores de la plataforma (solo ingresos SaaS)")
    fun overview() = admin.overview()

    @GetMapping("/plans")
    fun plans() = plans.adminPlans()

    @PostMapping("/plans")
    @ResponseStatus(HttpStatus.CREATED)
    fun createPlan(user: AuthenticatedUser, @Valid @RequestBody body: PlanRequest) = plans.create(user.userId, body)

    @PutMapping("/plans/{id}")
    fun updatePlan(user: AuthenticatedUser, @PathVariable id: UUID, @Valid @RequestBody body: PlanRequest) = plans.update(user.userId, id, body)

    @GetMapping("/businesses")
    @Operation(summary = "Negocios con su plan y estado (sin datos de compradores)")
    fun businesses(
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "ALL") filter: BusinessFilter,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ) = admin.businesses(q, filter, page, size)

    @GetMapping("/businesses/{id}")
    fun business(@PathVariable id: UUID) = admin.business(id)

    @PostMapping("/businesses/{id}/suspend")
    @Operation(summary = "Suspende el negocio: cierra la tienda y bloquea el panel comercial sin borrar datos")
    fun suspend(user: AuthenticatedUser, @PathVariable id: UUID, @Valid @RequestBody body: ReasonRequest): AdminBusinessDetail {
        admin.suspend(user.userId, id, body.reason!!)
        return admin.business(id)
    }

    @PostMapping("/businesses/{id}/reactivate")
    fun reactivate(user: AuthenticatedUser, @PathVariable id: UUID): AdminBusinessDetail {
        admin.reactivate(user.userId, id)
        return admin.business(id)
    }

    @GetMapping("/saas-payments")
    fun payments(
        @RequestParam(required = false) status: SaasPaymentStatus?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ) = admin.payments(status, page, size)

    @PostMapping("/saas-payments/{id}/verify")
    @Operation(summary = "Confirma la recepción real del pago y activa o extiende el plan")
    fun verify(user: AuthenticatedUser, @PathVariable id: UUID) {
        billing.verify(user.userId, id)
    }

    @PostMapping("/saas-payments/{id}/reject")
    fun reject(user: AuthenticatedUser, @PathVariable id: UUID, @Valid @RequestBody body: ReasonRequest) {
        billing.reject(user.userId, id, body.reason!!)
    }

    @GetMapping("/saas-payments/{id}/receipt")
    @Operation(summary = "Comprobante del pago SaaS (acceso auditado)")
    fun receipt(user: AuthenticatedUser, @PathVariable id: UUID): ResponseEntity<Resource> {
        val path = billing.receipt(user.userId, id)
        val type = ImageType.fromExtension(path.fileName.toString().substringAfterLast('.'))!!
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(type.contentType))
            .cacheControl(CacheControl.noStore())
            .header("Content-Disposition", "inline")
            .body(FileSystemResource(path))
    }

    @GetMapping("/audit-logs")
    @Operation(summary = "Auditoría. El detalle solo se muestra en acciones de la plataforma.")
    fun auditLogs(
        @RequestParam(required = false) businessId: UUID?,
        @RequestParam(required = false) action: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "30") size: Int,
    ) = admin.auditLogs(businessId, action, platformOnly = false, page = page, size = size)

    @GetMapping("/settings")
    fun settings() = admin.settings()

    @PutMapping("/settings")
    fun updateSettings(user: AuthenticatedUser, @Valid @RequestBody body: PlatformSettingsRequest) = admin.updateSettings(user.userId, body)

    @PutMapping("/settings/payment-methods/{method}")
    @Operation(summary = "Cuenta Yape/Plin de la plataforma donde los vendedores pagan su plan")
    fun updatePaymentMethod(user: AuthenticatedUser, @PathVariable method: String, @Valid @RequestBody body: PlatformPaymentMethodRequest) =
        admin.updatePaymentMethod(user.userId, parse(method), body)

    @PostMapping("/settings/payment-methods/{method}/qr", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadQr(user: AuthenticatedUser, @PathVariable method: String, @RequestPart("file") file: MultipartFile) =
        admin.replaceQr(user.userId, parse(method), file)

    @DeleteMapping("/settings/payment-methods/{method}/qr")
    fun deleteQr(user: AuthenticatedUser, @PathVariable method: String) = admin.replaceQr(user.userId, parse(method), null)

    private fun parse(method: String) =
        PaymentMethodType.entries.firstOrNull { it.name.equals(method, ignoreCase = true) } ?: throw ApiException.notFound("Método de cobro no soportado.")
}
