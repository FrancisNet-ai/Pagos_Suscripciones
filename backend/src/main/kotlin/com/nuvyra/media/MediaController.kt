package com.nuvyra.media

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

/** Imágenes públicas (logos, portadas, productos, QR). Los comprobantes de pago NO se sirven aquí. */
@RestController
@Tag(name = "Archivos públicos")
class MediaController(private val storage: MediaStorage) {

    @GetMapping("/api/v1/public/media/{key}")
    @Operation(summary = "Descarga una imagen pública por su clave aleatoria; `size=sm` pide la versión reducida si existe")
    fun get(@PathVariable key: String, @RequestParam(required = false) size: String?): ResponseEntity<Resource> {
        val original = storage.resolve(key) ?: return ResponseEntity.notFound().build()
        val path = (if (size == "sm") storage.resolveSmall(key) else null) ?: original
        val type = ImageType.fromExtension(path.fileName.toString().substringAfterLast('.')) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(type.contentType))
            // Las claves son únicas e inmutables: caché larga.
            .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
            .header("Content-Disposition", "inline")
            .body(FileSystemResource(path))
    }
}
