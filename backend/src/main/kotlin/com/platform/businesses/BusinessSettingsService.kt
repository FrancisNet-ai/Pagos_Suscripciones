package com.platform.businesses

import com.platform.common.audit.AuditActions
import com.platform.common.audit.AuditService
import com.platform.common.error.ApiException
import com.platform.common.text.SlugGenerator
import com.platform.common.validation.PeruPhone
import com.platform.media.MediaStorage
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.time.DateTimeException
import java.time.ZoneId
import java.util.UUID

enum class BusinessImageKind { LOGO, COVER }

@Service
class BusinessSettingsService(
    private val businesses: BusinessRepository,
    private val media: MediaStorage,
    private val audit: AuditService,
) {
    @Transactional
    fun update(businessId: UUID, actorId: UUID, request: UpdateBusinessRequest): Business {
        val business = load(businessId)
        val timeZone = request.timeZone!!.trim()
        try {
            ZoneId.of(timeZone)
        } catch (ex: DateTimeException) {
            throw ApiException.invalidField("timeZone", "Zona horaria no válida")
        }
        business.businessName = request.businessName!!.trim()
        business.description = request.description?.trim()?.ifEmpty { null }
        business.primaryColor = request.primaryColor?.uppercase()?.ifEmpty { null }
        business.whatsappNumber = request.whatsappNumber?.takeIf { it.isNotBlank() }?.let { PeruPhone.normalize(it) }
        business.timeZone = timeZone
        audit.record(AuditActions.BUSINESS_UPDATED, actorId = actorId, businessId = businessId, entityType = "BUSINESS", entityId = businessId)
        return business
    }

    @Transactional(readOnly = true)
    fun checkSlug(businessId: UUID, rawSlug: String): SlugAvailabilityResponse {
        val slug = rawSlug.trim().lowercase()
        val reason = slugProblem(businessId, slug)
        return SlugAvailabilityResponse(slug, reason == null, reason)
    }

    @Transactional
    fun changeSlug(businessId: UUID, actorId: UUID, rawSlug: String): Business {
        val business = load(businessId)
        val slug = rawSlug.trim().lowercase()
        if (slug == business.slug) return business
        slugProblem(businessId, slug)?.let { reason ->
            if (reason == SLUG_TAKEN) throw ApiException.conflict("SLUG_TAKEN", reason)
            throw ApiException.invalidField("slug", reason)
        }
        val previous = business.slug
        business.slug = slug
        // flush para que una colisión concurrente (UNIQUE) se detecte dentro de esta operación
        businesses.saveAndFlush(business)
        audit.record(
            AuditActions.BUSINESS_SLUG_CHANGED, actorId = actorId, businessId = businessId,
            entityType = "BUSINESS", entityId = businessId, metadata = mapOf("from" to previous, "to" to slug),
        )
        return business
    }

    @Transactional
    fun replaceImage(businessId: UUID, actorId: UUID, kind: BusinessImageKind, file: MultipartFile?): Business {
        val business = load(businessId)
        val newUrl = file?.let { media.storeImage(it) }
        val previous = when (kind) {
            BusinessImageKind.LOGO -> business.logoUrl.also { business.logoUrl = newUrl }
            BusinessImageKind.COVER -> business.coverUrl.also { business.coverUrl = newUrl }
        }
        media.deleteAfterCommit(previous)
        audit.record(
            AuditActions.BUSINESS_IMAGE_CHANGED, actorId = actorId, businessId = businessId,
            entityType = "BUSINESS", entityId = businessId, metadata = mapOf("kind" to kind.name, "removed" to (file == null)),
        )
        return business
    }

    private fun slugProblem(businessId: UUID, slug: String): String? = when {
        slug.length < 3 || slug.length > SlugGenerator.MAX_LENGTH -> "El enlace debe tener entre 3 y ${SlugGenerator.MAX_LENGTH} caracteres"
        !SLUG_FORMAT.matches(slug) -> "Usa solo letras minúsculas sin tildes, números y guiones entre palabras"
        slug in SlugGenerator.reserved -> "Este enlace está reservado"
        businesses.findBySlug(slug)?.let { it.id != businessId } == true -> SLUG_TAKEN
        else -> null
    }

    private fun load(businessId: UUID) = businesses.findById(businessId).orElseThrow { ApiException.notFound() }

    companion object {
        private val SLUG_FORMAT = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")
        private const val SLUG_TAKEN = "Este enlace ya está en uso por otro negocio"
    }
}
