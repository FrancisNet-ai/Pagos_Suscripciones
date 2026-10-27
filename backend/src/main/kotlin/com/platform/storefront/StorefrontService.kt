package com.platform.storefront

import com.platform.businesses.Business
import com.platform.businesses.BusinessRepository
import com.platform.saas.EntitlementService
import com.platform.saas.PlanFeature
import com.platform.capacity.CapacityService
import com.platform.common.error.ApiException
import com.platform.payments.PaymentMethodService
import com.platform.products.Product
import com.platform.products.ProductRepository
import com.platform.products.PublicationStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** Vista pública de una tienda. El slug solo localiza la tienda; no concede permisos. */
@Service
class StorefrontService(
    private val businesses: BusinessRepository,
    private val products: ProductRepository,
    private val paymentMethods: PaymentMethodService,
    private val capacity: CapacityService,
    private val entitlements: EntitlementService,
) {
    @Transactional(readOnly = true)
    fun store(slug: String): PublicStoreResponse {
        val business = findBusiness(slug)
        val access = entitlements.access(business)
        if (!access.active) {
            // Negocio suspendido o sin plan vigente: sin catálogo ni datos de contacto o cobro.
            return PublicStoreResponse(
                business.slug, business.businessName, null, null, null, null, false,
                "Esta tienda no está recibiendo pedidos por el momento.", emptyList(),
            )
        }
        val methods = paymentMethods.enabledMethods(business.id).map { PublicPaymentOption(it.method, it.method.label) }
        return PublicStoreResponse(
            slug = business.slug,
            businessName = business.businessName,
            description = business.description,
            logoUrl = business.logoUrl,
            // La personalización solo se aplica si el plan la incluye.
            coverUrl = business.coverUrl.takeIf { access.has(PlanFeature.BRANDING) },
            primaryColor = business.primaryColor.takeIf { access.has(PlanFeature.BRANDING) },
            acceptingOrders = methods.isNotEmpty(),
            notice = if (methods.isEmpty()) "Esta tienda aún no tiene métodos de pago habilitados." else null,
            paymentMethods = methods,
        )
    }

    @Transactional(readOnly = true)
    fun products(slug: String): List<PublicProductResponse> {
        val business = findBusiness(slug)
        if (!entitlements.canSell(business)) return emptyList()
        val list = products.findByBusinessIdAndPublicationStatusOrderByCreatedAtDesc(business.id, PublicationStatus.PUBLISHED)
        val availability = capacity.availabilityFor(list)
        return list.map { toPublic(it, availability.getValue(it.id)) }
    }

    @Transactional(readOnly = true)
    fun product(slug: String, productId: UUID): PublicProductResponse {
        val business = findBusiness(slug)
        if (!entitlements.canSell(business)) throw ApiException.notFound("Esta tienda no está recibiendo pedidos por el momento.")
        val product = publishedProduct(business, productId)
        return toPublic(product, capacity.availability(product))
    }

    fun findBusiness(slug: String): Business =
        businesses.findBySlug(slug.lowercase()) ?: throw ApiException.notFound("No encontramos esta tienda.")

    /** El producto debe pertenecer al negocio del slug y estar publicado. */
    fun publishedProduct(business: Business, productId: UUID): Product =
        products.findByIdAndBusinessId(productId, business.id)?.takeIf { it.publicationStatus == PublicationStatus.PUBLISHED }
            ?: throw ApiException.notFound("Este producto no está disponible en la tienda.")

    private fun toPublic(p: Product, a: com.platform.capacity.Availability) = PublicProductResponse(
        id = p.id,
        name = p.name,
        description = p.description,
        category = p.category,
        productType = p.productType,
        price = p.price,
        currency = "PEN",
        durationDays = p.durationDays,
        imageUrl = p.imageUrl,
        availableSlots = a.available,
        soldOut = a.soldOut,
        purchasable = p.available && !a.soldOut,
    )
}
