package com.nuvyra.businesses

import com.nuvyra.common.error.ApiException
import com.nuvyra.common.text.SecureTokens
import com.nuvyra.common.text.SlugGenerator
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class BusinessService(
    private val businesses: BusinessRepository,
    private val members: BusinessMemberRepository,
) {
    /** Crea el negocio y la membresía OWNER. Debe ejecutarse dentro de la transacción del registro. */
    @Transactional
    fun createForOwner(ownerId: UUID, businessName: String, whatsappNumber: String?): Business {
        val business = businesses.save(
            Business(
                ownerId = ownerId,
                uniqueCode = generateUniqueCode(),
                slug = availableSlug(businessName),
                businessName = businessName.trim(),
                whatsappNumber = whatsappNumber,
            ),
        )
        members.save(BusinessMember(businessId = business.id, userId = ownerId, role = BusinessRole.OWNER))
        return business
    }

    @Transactional(readOnly = true)
    fun get(businessId: UUID): Business = businesses.findById(businessId).orElseThrow { ApiException.notFound() }

    private fun availableSlug(businessName: String): String {
        val base = SlugGenerator.slugify(businessName)
        if (!businesses.existsBySlug(base)) return base
        // Sufijo aleatorio en lugar de secuencial: evita carreras y enumeración de tiendas.
        repeat(8) {
            val candidate = "${base.take(SlugGenerator.MAX_LENGTH - 5)}-${SecureTokens.readableCode(4).lowercase()}"
            if (!businesses.existsBySlug(candidate)) return candidate
        }
        throw ApiException.conflict("SLUG_UNAVAILABLE", "No se pudo generar un enlace único para tu negocio. Inténtalo de nuevo.")
    }

    private fun generateUniqueCode(): String {
        repeat(8) {
            val code = "NEG-${SecureTokens.readableCode(8)}"
            if (!businesses.existsByUniqueCode(code)) return code
        }
        throw IllegalStateException("No se pudo generar un código empresarial único")
    }
}
