package com.nuvyra.media

import com.nuvyra.common.error.ApiException
import javax.imageio.ImageIO

enum class ImageType(val extension: String, val contentType: String) {
    PNG("png", "image/png"),
    JPEG("jpg", "image/jpeg"),
    WEBP("webp", "image/webp"),
    ;

    companion object {
        fun fromExtension(extension: String) = entries.firstOrNull { it.extension == extension }
    }
}

/**
 * Valida imágenes por su contenido real (firma de bytes), no por el nombre ni el Content-Type
 * enviado por el navegador. Para PNG y JPEG también lee las dimensiones de la cabecera sin
 * decodificar la imagen completa (evita "bombas" de descompresión).
 */
object ImageValidator {
    private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    fun detect(bytes: ByteArray): ImageType? = when {
        bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(PNG_SIGNATURE) -> ImageType.PNG
        bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> ImageType.JPEG
        bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> ImageType.WEBP
        else -> null
    }

    fun validate(bytes: ByteArray, maxBytes: Long, maxDimension: Int): ImageType {
        if (bytes.isEmpty()) throw ApiException.badRequest("INVALID_IMAGE", "El archivo está vacío.")
        if (bytes.size > maxBytes) {
            throw ApiException.badRequest("FILE_TOO_LARGE", "La imagen supera el tamaño máximo de ${maxBytes / (1024 * 1024)} MB.")
        }
        val type = detect(bytes)
            ?: throw ApiException.badRequest("INVALID_IMAGE", "Formato no permitido. Usa una imagen PNG, JPG o WebP.")
        if (type != ImageType.WEBP) checkDimensions(bytes, maxDimension)
        return type
    }

    private fun checkDimensions(bytes: ByteArray, maxDimension: Int) {
        ImageIO.createImageInputStream(bytes.inputStream()).use { input ->
            val reader = ImageIO.getImageReaders(input).asSequence().firstOrNull()
                ?: throw ApiException.badRequest("INVALID_IMAGE", "La imagen está dañada o no se puede leer.")
            try {
                reader.input = input
                val width = reader.getWidth(0)
                val height = reader.getHeight(0)
                if (width > maxDimension || height > maxDimension) {
                    throw ApiException.badRequest("IMAGE_TOO_LARGE", "La imagen supera ${maxDimension}×$maxDimension píxeles.")
                }
            } catch (ex: ApiException) {
                throw ex
            } catch (ex: Exception) {
                throw ApiException.badRequest("INVALID_IMAGE", "La imagen está dañada o no se puede leer.")
            } finally {
                reader.dispose()
            }
        }
    }
}
