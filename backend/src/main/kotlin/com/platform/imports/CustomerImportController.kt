package com.platform.imports

import com.platform.security.CurrentBusiness
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
@RequestMapping("/api/v1/customers/imports")
@Tag(name = "Importación de clientes")
class CustomerImportController(private val service: CustomerImportService) {

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Analiza un CSV o Excel (.xlsx) y devuelve la vista previa. No crea nada todavía.")
    fun preview(current: CurrentBusiness, @RequestPart("file") file: MultipartFile) =
        service.preview(current.businessId, current.userId, file.originalFilename ?: "clientes", file.bytes)

    @GetMapping("/{id}")
    fun get(current: CurrentBusiness, @PathVariable id: UUID) = service.get(current.businessId, id)

    @PostMapping("/{id}/commit")
    @Operation(summary = "Aplica la vista previa: crea clientes y registra sus suscripciones")
    fun commit(current: CurrentBusiness, @PathVariable id: UUID) = service.commit(current.businessId, current.userId, id)

    @PostMapping("/{id}/discard")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun discard(current: CurrentBusiness, @PathVariable id: UUID) = service.discard(current.businessId, id)
}
