package com.platform.products

import com.platform.saas.EntitlementService
import com.platform.capacity.CapacityService
import com.platform.common.audit.AuditActions
import com.platform.common.audit.AuditService
import com.platform.common.error.ApiException
import com.platform.common.web.PageResponse
import com.platform.media.MediaStorage
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.math.RoundingMode
import java.util.UUID

@Service
class ProductService(
    private val products: ProductRepository,
    private val capacity: CapacityService,
    private val media: MediaStorage,
    private val audit: AuditService,
    private val entitlements: EntitlementService,
) {
    @Transactional(readOnly = true)
    fun list(businessId: UUID, query: String?, filter: ProductFilter, page: Int, size: Int): PageResponse<ProductResponse> {
        val pageable = PageRequest.of(maxOf(page, 0), size.coerceIn(1, PageResponse.MAX_SIZE), Sort.by(Sort.Direction.DESC, "createdAt"))
        val result = products.search(businessId, filter.statuses, likePattern(query), pageable)
        val availability = capacity.availabilityFor(result.content)
        return PageResponse.of(result) { ProductResponse.from(it, availability.getValue(it.id)) }
    }

    @Transactional(readOnly = true)
    fun get(businessId: UUID, productId: UUID): ProductResponse = toResponse(load(businessId, productId))

    @Transactional
    fun create(businessId: UUID, actorId: UUID, request: ProductRequest): ProductResponse {
        validate(request)
        entitlements.ensureProductCapacity(businessId)
        val product = products.save(
            Product(
                businessId = businessId,
                name = request.name!!.trim(),
                description = request.description.clean(),
                category = request.category.clean(),
                productType = request.productType!!,
                price = request.price!!.setScale(2, RoundingMode.UNNECESSARY),
                durationDays = request.durationDays,
                capacityEnabled = request.capacityEnabled!!,
                capacity = request.capacity.takeIf { request.capacityEnabled },
                available = request.available!!,
            ),
        )
        audit.record(AuditActions.PRODUCT_CREATED, actorId = actorId, businessId = businessId, entityType = "PRODUCT", entityId = product.id)
        return toResponse(product)
    }

    @Transactional
    fun update(businessId: UUID, actorId: UUID, productId: UUID, request: ProductRequest): ProductResponse {
        validate(request)
        // Bloqueo para que la verificación de capacidad no compita con una reserva simultánea.
        val product = products.lockByIdAndBusinessId(productId, businessId) ?: throw ApiException.notFound("El producto no existe.")
        if (request.capacityEnabled!!) {
            val usage = capacity.availability(product)
            val inUse = usage.occupied + usage.reserved
            if (request.capacity!! < inUse) {
                throw ApiException.conflict(
                    "CAPACITY_BELOW_USAGE",
                    "La capacidad no puede ser menor que los cupos en uso ($inUse). Libera cupos antes de reducirla.",
                )
            }
        }
        val previousPrice = product.price
        product.apply {
            name = request.name!!.trim()
            description = request.description.clean()
            category = request.category.clean()
            productType = request.productType!!
            price = request.price!!.setScale(2, RoundingMode.UNNECESSARY)
            durationDays = request.durationDays
            capacityEnabled = request.capacityEnabled
            capacity = request.capacity.takeIf { request.capacityEnabled }
            available = request.available!!
        }
        audit.record(
            AuditActions.PRODUCT_UPDATED, actorId = actorId, businessId = businessId, entityType = "PRODUCT", entityId = productId,
            metadata = if (previousPrice.compareTo(product.price) != 0) mapOf("priceFrom" to previousPrice, "priceTo" to product.price) else emptyMap(),
        )
        return toResponse(product)
    }

    /** Publicar, despublicar o archivar. Archivar nunca elimina el historial de ventas. */
    @Transactional
    fun changeStatus(businessId: UUID, actorId: UUID, productId: UUID, status: PublicationStatus): ProductResponse {
        val product = load(businessId, productId)
        val previous = product.publicationStatus
        // Sacar un producto del archivo vuelve a contar para el límite del plan.
        if (previous == PublicationStatus.ARCHIVED && status != PublicationStatus.ARCHIVED) entitlements.ensureProductCapacity(businessId)
        if (previous != status) {
            product.publicationStatus = status
            audit.record(
                AuditActions.PRODUCT_STATUS_CHANGED, actorId = actorId, businessId = businessId, entityType = "PRODUCT", entityId = productId,
                metadata = mapOf("from" to previous.name, "to" to status.name),
            )
        }
        return toResponse(product)
    }

    @Transactional
    fun replaceImage(businessId: UUID, actorId: UUID, productId: UUID, file: MultipartFile?): ProductResponse {
        val product = load(businessId, productId)
        val previous = product.imageUrl
        product.imageUrl = file?.let { media.storeImage(it) }
        media.deleteAfterCommit(previous)
        audit.record(AuditActions.PRODUCT_UPDATED, actorId = actorId, businessId = businessId, entityType = "PRODUCT", entityId = productId, metadata = mapOf("image" to (file != null)))
        return toResponse(product)
    }

    private fun validate(request: ProductRequest) {
        if (request.productType == ProductType.SUBSCRIPTION && request.durationDays == null) {
            throw ApiException.invalidField("durationDays", "Las suscripciones necesitan una duración en días")
        }
        if (request.capacityEnabled == true && request.capacity == null) {
            throw ApiException.invalidField("capacity", "Indica la capacidad total de cupos")
        }
    }

    private fun load(businessId: UUID, productId: UUID) =
        products.findByIdAndBusinessId(productId, businessId) ?: throw ApiException.notFound("El producto no existe.")

    private fun toResponse(product: Product) = ProductResponse.from(product, capacity.availability(product))

    private fun String?.clean() = this?.trim()?.ifEmpty { null }

    companion object {
        fun likePattern(query: String?): String {
            val q = query?.trim()?.lowercase()?.take(100).orEmpty()
            val escaped = q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
            return "%$escaped%"
        }
    }
}
