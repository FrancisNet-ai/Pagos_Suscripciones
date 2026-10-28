package com.nuvyra.whatsapp

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

enum class IntegrationStatus { UNVERIFIED, VERIFIED, ERROR }

@Entity
@Table(name = "whatsapp_integrations")
class WhatsAppIntegration(
    @Id val businessId: UUID,
    @Column(nullable = false) var phoneNumberId: String,
    var wabaId: String? = null,
    var displayPhone: String? = null,
    @Column(nullable = false) var accessTokenEncrypted: String,
    @Column(nullable = false) var reminderTemplate: String,
    var farewellTemplate: String? = null,
    /** Confirmación al reportar el pago y al verificarlo (opcionales). */
    var orderTemplate: String? = null,
    var paymentTemplate: String? = null,
    @Column(nullable = false) var templateLanguage: String = "es",
    @Column(nullable = false) var enabled: Boolean = false,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var status: IntegrationStatus = IntegrationStatus.UNVERIFIED,
    var lastVerifiedAt: Instant? = null,
    var lastError: String? = null,
    @Version @Column(nullable = false) var version: Long = 0,
    @CreationTimestamp @Column(nullable = false, updatable = false) var createdAt: Instant? = null,
    @UpdateTimestamp @Column(nullable = false) var updatedAt: Instant? = null,
) {
    val active: Boolean get() = enabled && status == IntegrationStatus.VERIFIED
}

interface WhatsAppIntegrationRepository : JpaRepository<WhatsAppIntegration, UUID> {
    fun findByPhoneNumberId(phoneNumberId: String): WhatsAppIntegration?
    fun existsByPhoneNumberIdAndBusinessIdNot(phoneNumberId: String, businessId: UUID): Boolean
}

private const val GRAPH_ID = "^[0-9]{5,30}$"
private const val TEMPLATE = "^[a-z0-9_]{1,512}$"

data class WhatsAppIntegrationRequest(
    @field:NotBlank(message = "Ingresa el identificador del número de teléfono")
    @field:Pattern(regexp = GRAPH_ID, message = "Es un número largo que aparece en WhatsApp Manager (solo dígitos)")
    val phoneNumberId: String?,
    @field:Pattern(regexp = "^$|$GRAPH_ID", message = "Es un número largo que aparece en WhatsApp Manager (solo dígitos)")
    val wabaId: String?,
    /** Vacío al editar = conservar el token guardado. */
    @field:Size(max = 1024, message = "El token es demasiado largo")
    val accessToken: String?,
    @field:NotBlank(message = "Ingresa el nombre de la plantilla de recordatorio")
    @field:Pattern(regexp = TEMPLATE, message = "Usa el nombre exacto de la plantilla: minúsculas, números y guion bajo")
    val reminderTemplate: String?,
    @field:Pattern(regexp = "^$|$TEMPLATE", message = "Usa el nombre exacto de la plantilla: minúsculas, números y guion bajo")
    val farewellTemplate: String?,
    @field:Pattern(regexp = "^$|$TEMPLATE", message = "Usa el nombre exacto de la plantilla: minúsculas, números y guion bajo")
    val orderTemplate: String? = null,
    @field:Pattern(regexp = "^$|$TEMPLATE", message = "Usa el nombre exacto de la plantilla: minúsculas, números y guion bajo")
    val paymentTemplate: String? = null,
    @field:NotBlank @field:Pattern(regexp = "^[a-z]{2}(_[A-Z]{2})?$", message = "Usa un código de idioma como es o es_PE")
    val templateLanguage: String?,
    @field:NotNull val enabled: Boolean?,
)

data class WhatsAppIntegrationResponse(
    val configured: Boolean,
    val phoneNumberId: String?,
    val wabaId: String?,
    val displayPhone: String?,
    val tokenSaved: Boolean,
    val reminderTemplate: String?,
    val farewellTemplate: String?,
    val orderTemplate: String?,
    val paymentTemplate: String?,
    val templateLanguage: String,
    val enabled: Boolean,
    val status: IntegrationStatus?,
    val lastVerifiedAt: Instant?,
    val lastError: String?,
    /** El plan del negocio incluye avisos automáticos. */
    val planAllows: Boolean,
    /** La plataforma configuró la app de Meta (webhooks firmados): estados de entrega y respuestas Sí/No. */
    val webhooksAvailable: Boolean,
    val webhookUrl: String,
)
