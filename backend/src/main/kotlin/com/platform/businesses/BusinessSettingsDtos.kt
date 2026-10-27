package com.platform.businesses

import com.platform.common.validation.PeruvianMobile
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class UpdateBusinessRequest(
    @field:NotBlank(message = "Ingresa el nombre comercial")
    @field:Size(min = 2, max = 120, message = "El nombre debe tener entre 2 y 120 caracteres")
    val businessName: String?,

    @field:Size(max = 1000, message = "La descripción admite hasta 1000 caracteres")
    val description: String?,

    @field:Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Usa un color hexadecimal, por ejemplo #1A3D63")
    val primaryColor: String?,

    @field:PeruvianMobile(message = "Ingresa un número de WhatsApp peruano válido")
    val whatsappNumber: String?,

    @field:NotBlank(message = "Selecciona una zona horaria")
    @field:Size(max = 64)
    val timeZone: String?,
)

data class UpdateSlugRequest(
    @field:NotBlank(message = "Ingresa el enlace")
    @field:Size(max = 60)
    val slug: String?,
)

data class SlugAvailabilityResponse(val slug: String, val available: Boolean, val reason: String?)
