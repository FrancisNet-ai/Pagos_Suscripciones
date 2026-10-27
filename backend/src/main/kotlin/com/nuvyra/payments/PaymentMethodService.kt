package com.nuvyra.payments

import com.nuvyra.common.audit.AuditActions
import com.nuvyra.common.audit.AuditService
import com.nuvyra.common.error.ApiException
import com.nuvyra.common.validation.PeruPhone
import com.nuvyra.common.validation.PeruvianMobile
import com.nuvyra.media.MediaStorage
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

data class UpdatePaymentMethodRequest(
    @field:NotBlank(message = "Ingresa el nombre del titular")
    @field:Size(min = 3, max = 120, message = "El nombre del titular debe tener entre 3 y 120 caracteres")
    val holderName: String?,

    @field:NotBlank(message = "Ingresa el celular que recibe los pagos")
    @field:PeruvianMobile
    val phone: String?,

    @field:NotNull(message = "Indica si el método está habilitado")
    val enabled: Boolean?,
)

data class PaymentMethodResponse(
    val method: PaymentMethodType,
    val label: String,
    val configured: Boolean,
    val holderName: String?,
    val phone: String?,
    val qrImageUrl: String?,
    val enabled: Boolean,
) {
    companion object {
        fun from(m: BusinessPaymentMethod) =
            PaymentMethodResponse(m.method, m.method.label, true, m.holderName, m.phone, m.qrImageUrl, m.enabled)

        fun empty(method: PaymentMethodType) = PaymentMethodResponse(method, method.label, false, null, null, null, false)
    }
}

@Service
class PaymentMethodService(
    private val repository: BusinessPaymentMethodRepository,
    private val media: MediaStorage,
    private val audit: AuditService,
) {
    /** Siempre devuelve Yape y Plin, configurados o no. */
    @Transactional(readOnly = true)
    fun list(businessId: UUID): List<PaymentMethodResponse> {
        val existing = repository.findByBusinessIdOrderByMethodAsc(businessId).associateBy { it.method }
        return PaymentMethodType.entries.map { type -> existing[type]?.let(PaymentMethodResponse::from) ?: PaymentMethodResponse.empty(type) }
    }

    @Transactional
    fun upsert(businessId: UUID, actorId: UUID, method: PaymentMethodType, request: UpdatePaymentMethodRequest): PaymentMethodResponse {
        val phone = PeruPhone.normalize(request.phone) ?: throw ApiException.invalidField("phone", "Ingresa un celular peruano válido de 9 dígitos")
        val holder = request.holderName!!.trim().replace(Regex("\\s+"), " ")
        val entity = repository.findByBusinessIdAndMethod(businessId, method)
            ?.apply {
                holderName = holder
                this.phone = phone
                enabled = request.enabled!!
            }
            ?: repository.save(BusinessPaymentMethod(businessId, method, holder, phone, enabled = request.enabled!!))
        audit.record(
            AuditActions.PAYMENT_METHOD_UPDATED, actorId = actorId, businessId = businessId,
            entityType = "PAYMENT_METHOD", entityId = entity.id, metadata = mapOf("method" to method.name, "enabled" to entity.enabled),
        )
        return PaymentMethodResponse.from(entity)
    }

    @Transactional
    fun replaceQr(businessId: UUID, actorId: UUID, method: PaymentMethodType, file: MultipartFile?): PaymentMethodResponse {
        val entity = repository.findByBusinessIdAndMethod(businessId, method)
            ?: throw ApiException.conflict("PAYMENT_METHOD_NOT_CONFIGURED", "Primero guarda el titular y el celular de ${method.label}.")
        val previous = entity.qrImageUrl
        entity.qrImageUrl = file?.let { media.storeImage(it) }
        media.deleteAfterCommit(previous)
        audit.record(
            AuditActions.PAYMENT_METHOD_UPDATED, actorId = actorId, businessId = businessId,
            entityType = "PAYMENT_METHOD", entityId = entity.id, metadata = mapOf("method" to method.name, "qr" to (file != null)),
        )
        return PaymentMethodResponse.from(entity)
    }

    /** Métodos habilitados de un negocio (usado por la tienda pública en la Fase 3). */
    @Transactional(readOnly = true)
    fun enabledMethods(businessId: UUID): List<BusinessPaymentMethod> =
        repository.findByBusinessIdOrderByMethodAsc(businessId).filter { it.enabled }
}
