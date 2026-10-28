package com.nuvyra.whatsapp

import com.nuvyra.common.audit.AuditActions
import com.nuvyra.common.audit.AuditService
import com.nuvyra.common.config.AppProperties
import com.nuvyra.common.error.ApiException
import com.nuvyra.common.text.SecretCipher
import com.nuvyra.saas.EntitlementService
import com.nuvyra.saas.PlanFeature
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

/** Credenciales en uso para enviar mensajes en nombre de un negocio (nunca salen del servidor). */
data class ActiveWhatsApp(val integration: WhatsAppIntegration, val token: String)

/**
 * Conexión de un negocio con WhatsApp Business Platform. Antes de habilitar el envío se verifica el
 * número contra la Graph API con el token entregado; el token se guarda cifrado y nunca se devuelve.
 */
@Service
class WhatsAppIntegrationService(
    private val integrations: WhatsAppIntegrationRepository,
    private val client: WhatsAppCloudClient,
    private val cipher: SecretCipher,
    private val entitlements: EntitlementService,
    private val properties: AppProperties,
    private val audit: AuditService,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun get(businessId: UUID): WhatsAppIntegrationResponse = toResponse(businessId, integrations.findById(businessId).orElse(null))

    @Transactional
    fun save(businessId: UUID, actorId: UUID, request: WhatsAppIntegrationRequest): WhatsAppIntegrationResponse {
        entitlements.requireFeature(businessId, PlanFeature.REMINDERS)
        val phoneNumberId = request.phoneNumberId!!.trim()
        if (integrations.existsByPhoneNumberIdAndBusinessIdNot(phoneNumberId, businessId)) {
            throw ApiException.invalidField("phoneNumberId", "Este número ya está conectado a otro negocio")
        }
        val existing = integrations.findById(businessId).orElse(null)
        val newToken = request.accessToken?.trim()?.ifEmpty { null }
        if (existing == null && newToken == null) throw ApiException.invalidField("accessToken", "Ingresa el token de acceso")
        val token = newToken ?: cipher.decrypt(existing!!.accessTokenEncrypted, businessId.toString())
        val encrypted = newToken?.let { cipher.encrypt(it, businessId.toString()) } ?: existing!!.accessTokenEncrypted

        val integration = existing ?: WhatsAppIntegration(
            businessId = businessId, phoneNumberId = phoneNumberId, accessTokenEncrypted = encrypted, reminderTemplate = request.reminderTemplate!!,
        )
        integration.phoneNumberId = phoneNumberId
        integration.wabaId = request.wabaId?.trim()?.ifEmpty { null }
        integration.accessTokenEncrypted = encrypted
        integration.reminderTemplate = request.reminderTemplate!!.trim()
        integration.farewellTemplate = request.farewellTemplate?.trim()?.ifEmpty { null }
        integration.orderTemplate = request.orderTemplate?.trim()?.ifEmpty { null }
        integration.paymentTemplate = request.paymentTemplate?.trim()?.ifEmpty { null }
        integration.templateLanguage = request.templateLanguage!!.trim()

        // Verificación real contra Meta: sin ella el envío automático no se habilita.
        try {
            val info = client.phoneNumber(phoneNumberId, token)
            integration.displayPhone = info.displayPhoneNumber
            integration.status = IntegrationStatus.VERIFIED
            integration.lastVerifiedAt = clock.instant()
            integration.lastError = null
            integration.wabaId?.let { waba ->
                runCatching { client.subscribeApp(waba, token) }.onFailure {
                    integration.lastError = "Número verificado, pero no se pudo suscribir la cuenta a los webhooks: ${it.message}".take(480)
                }
            }
            integration.enabled = request.enabled!!
        } catch (ex: WhatsAppApiException) {
            integration.status = IntegrationStatus.ERROR
            integration.lastError = ex.message?.take(480)
            integration.enabled = false
        }
        integrations.save(integration)
        audit.record(
            AuditActions.WHATSAPP_INTEGRATION_UPDATED, actorId = actorId, businessId = businessId, entityType = "WHATSAPP_INTEGRATION", entityId = businessId,
            metadata = mapOf("status" to integration.status.name, "enabled" to integration.enabled, "tokenChanged" to (newToken != null)),
        )
        return toResponse(businessId, integration)
    }

    @Transactional
    fun remove(businessId: UUID, actorId: UUID) {
        val existing = integrations.findById(businessId).orElse(null) ?: return
        integrations.delete(existing)
        audit.record(AuditActions.WHATSAPP_INTEGRATION_REMOVED, actorId = actorId, businessId = businessId, entityType = "WHATSAPP_INTEGRATION", entityId = businessId)
    }

    /** Integración habilitada y verificada, con el plan vigente que incluye avisos automáticos. */
    @Transactional(readOnly = true)
    fun active(businessId: UUID): ActiveWhatsApp? {
        val i = integrations.findById(businessId).orElse(null)?.takeIf { it.active } ?: return null
        if (!entitlements.hasFeature(businessId, PlanFeature.REMINDERS)) return null
        return ActiveWhatsApp(i, cipher.decrypt(i.accessTokenEncrypted, businessId.toString()))
    }

    /** Integración activa sin descifrar el token (para decidir qué ofrecer). */
    @Transactional(readOnly = true)
    fun isActive(businessId: UUID): Boolean =
        integrations.findById(businessId).orElse(null)?.active == true && entitlements.hasFeature(businessId, PlanFeature.REMINDERS)

    @Transactional(readOnly = true)
    fun activeByPhoneNumberId(phoneNumberId: String): ActiveWhatsApp? =
        integrations.findByPhoneNumberId(phoneNumberId)?.let { active(it.businessId) }

    /** Desactiva el envío si Meta rechaza el token (p. ej. revocado), para no insistir con cada aviso. */
    @Transactional
    fun markBroken(businessId: UUID, reason: String) {
        integrations.findById(businessId).ifPresent {
            it.status = IntegrationStatus.ERROR
            it.enabled = false
            it.lastError = reason.take(480)
        }
    }

    val webhooksAvailable: Boolean get() = properties.whatsapp.appSecret.isNotBlank() && properties.whatsapp.verifyToken.isNotBlank()

    private fun toResponse(businessId: UUID, i: WhatsAppIntegration?) = WhatsAppIntegrationResponse(
        configured = i != null,
        phoneNumberId = i?.phoneNumberId,
        wabaId = i?.wabaId,
        displayPhone = i?.displayPhone,
        tokenSaved = i != null,
        reminderTemplate = i?.reminderTemplate,
        farewellTemplate = i?.farewellTemplate,
        orderTemplate = i?.orderTemplate,
        paymentTemplate = i?.paymentTemplate,
        templateLanguage = i?.templateLanguage ?: "es",
        enabled = i?.enabled ?: false,
        status = i?.status,
        lastVerifiedAt = i?.lastVerifiedAt,
        lastError = i?.lastError,
        planAllows = entitlements.hasFeature(businessId, PlanFeature.REMINDERS),
        webhooksAvailable = webhooksAvailable,
        webhookUrl = "${properties.publicBaseUrl.trimEnd('/')}/api/v1/public/webhooks/whatsapp",
    )
}
