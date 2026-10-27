package com.platform.businesses

import com.platform.security.CurrentBusiness
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
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
@RequestMapping("/api/v1/business")
@Tag(name = "Negocio")
class BusinessController(private val businessService: BusinessService) {

    @GetMapping("/me")
    @Operation(summary = "Negocio del usuario autenticado (resuelto en el servidor)")
    fun me(current: CurrentBusiness): BusinessResponse = BusinessResponse.from(businessService.get(current.businessId))
}
