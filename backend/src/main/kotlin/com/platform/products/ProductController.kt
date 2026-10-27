package com.platform.products

import com.platform.common.web.PageResponse
import com.platform.security.CurrentBusiness
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Productos")
class ProductController(private val service: ProductService) {

    @GetMapping
    @Operation(summary = "Lista los productos del negocio con búsqueda, filtro y paginación")
    fun list(
        current: CurrentBusiness,
        @RequestParam(required = false) query: String?,
        @RequestParam(defaultValue = "ACTIVE") filter: ProductFilter,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<ProductResponse> = service.list(current.businessId, query, filter, page, size)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea un producto (se crea como borrador)")
    fun create(current: CurrentBusiness, @Valid @RequestBody body: ProductRequest) =
        service.create(current.businessId, current.userId, body)

    @GetMapping("/{id}")
    fun get(current: CurrentBusiness, @PathVariable id: UUID) = service.get(current.businessId, id)

    @PutMapping("/{id}")
    @Operation(summary = "Actualiza un producto. Los pedidos existentes conservan su precio histórico.")
    fun update(current: CurrentBusiness, @PathVariable id: UUID, @Valid @RequestBody body: ProductRequest) =
        service.update(current.businessId, current.userId, id, body)

    @PatchMapping("/{id}/status")
    @Operation(summary = "Publica, despublica o archiva un producto")
    fun changeStatus(current: CurrentBusiness, @PathVariable id: UUID, @Valid @RequestBody body: ProductStatusRequest) =
        service.changeStatus(current.businessId, current.userId, id, body.publicationStatus!!)

    @PostMapping("/{id}/image", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(summary = "Sube la imagen del producto (PNG, JPG o WebP, máx. 2 MB)")
    fun uploadImage(current: CurrentBusiness, @PathVariable id: UUID, @RequestPart("file") file: MultipartFile) =
        service.replaceImage(current.businessId, current.userId, id, file)

    @DeleteMapping("/{id}/image")
    fun deleteImage(current: CurrentBusiness, @PathVariable id: UUID) =
        service.replaceImage(current.businessId, current.userId, id, null)
}
