package com.nuvyra.businesses

import com.nuvyra.security.CurrentBusiness
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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.time.Instant
import java.util.UUID

data class BusinessResponse(
    val id: UUID,
    val uniqueCode: String,
    val slug: String,
    val businessName: String,
    val description: String?,
    val logoUrl: String?,
    val coverUrl: String?,
    val primaryColor: String?,
    val whatsappNumber: String?,
    val timeZone: String,
    val status: BusinessStatus,
    val createdAt: Instant?,
) {
    companion object {
        fun from(b: Business) = BusinessResponse(
            b.id, b.uniqueCode, b.slug, b.businessName, b.description, b.logoUrl, b.coverUrl,
            b.primaryColor, b.whatsappNumber, b.timeZone, b.status, b.createdAt,
        )
    }
}

@RestController
@RequestMapping("/api/v1/business/me")
@Tag(name = "Negocio")
class BusinessController(
    private val businessService: BusinessService,
    private val settings: BusinessSettingsService,
) {

    @GetMapping
    @Operation(summary = "Negocio del usuario autenticado (resuelto en el servidor)")
    fun me(current: CurrentBusiness): BusinessResponse = BusinessResponse.from(businessService.get(current.businessId))

    @PutMapping
    @Operation(summary = "Actualiza los datos y la personalización del negocio")
    fun update(current: CurrentBusiness, @Valid @RequestBody body: UpdateBusinessRequest): BusinessResponse =
        BusinessResponse.from(settings.update(current.businessId, current.userId, body))

    @GetMapping("/slug-availability")
    @Operation(summary = "Comprueba si un enlace público está disponible")
    fun slugAvailability(current: CurrentBusiness, @RequestParam slug: String): SlugAvailabilityResponse =
        settings.checkSlug(current.businessId, slug.take(80))

    @PutMapping("/slug")
    @Operation(summary = "Cambia el enlace público de la tienda")
    fun changeSlug(current: CurrentBusiness, @Valid @RequestBody body: UpdateSlugRequest): BusinessResponse =
        BusinessResponse.from(settings.changeSlug(current.businessId, current.userId, body.slug!!))

    @PostMapping("/{kind:logo|cover}", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(summary = "Sube el logotipo o la portada (PNG, JPG o WebP, máx. 2 MB)")
    fun uploadImage(current: CurrentBusiness, @PathVariable kind: String, @RequestPart("file") file: MultipartFile): BusinessResponse =
        BusinessResponse.from(settings.replaceImage(current.businessId, current.userId, imageKind(kind), file))

    @DeleteMapping("/{kind:logo|cover}")
    @Operation(summary = "Elimina el logotipo o la portada")
    fun deleteImage(current: CurrentBusiness, @PathVariable kind: String): BusinessResponse =
        BusinessResponse.from(settings.replaceImage(current.businessId, current.userId, imageKind(kind), null))

    private fun imageKind(kind: String) = if (kind == "logo") BusinessImageKind.LOGO else BusinessImageKind.COVER
}
