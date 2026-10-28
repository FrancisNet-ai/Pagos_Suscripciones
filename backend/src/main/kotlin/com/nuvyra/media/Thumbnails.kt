package com.nuvyra.media

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

data class EncodedImage(val bytes: ByteArray, val type: ImageType)

/**
 * Versiones reducidas para catálogos y móviles. Solo se generan si reducen el tamaño: una foto
 * opaca pasa a JPEG; una imagen con transparencia sigue en PNG. Las WebP ya son livianas y Java
 * no las decodifica sin bibliotecas nativas, así que se sirven tal cual.
 */
object Thumbnails {
    private const val JPEG_QUALITY = 0.82f

    fun create(original: ByteArray, maxWidth: Int): EncodedImage? {
        if (ImageValidator.detect(original) == ImageType.WEBP) return null
        // Las dimensiones ya se validaron al guardar el original: decodificarla no es una «bomba».
        val source = runCatching { ImageIO.read(original.inputStream()) }.getOrNull() ?: return null
        val width = minOf(maxWidth, source.width)
        val height = maxOf(1, Math.round(source.height * (width.toDouble() / source.width)).toInt())
        val alpha = source.colorModel.hasAlpha()
        val scaled = BufferedImage(width, height, if (alpha) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB)
        scaled.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            drawImage(source, 0, 0, width, height, null)
            dispose()
        }
        val encoded = if (alpha) EncodedImage(png(scaled), ImageType.PNG) else EncodedImage(jpeg(scaled), ImageType.JPEG)
        return encoded.takeIf { it.bytes.size < original.size }
    }

    private fun png(image: BufferedImage): ByteArray =
        ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()

    private fun jpeg(image: BufferedImage): ByteArray {
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val out = ByteArrayOutputStream()
        try {
            ImageIO.createImageOutputStream(out).use { stream ->
                writer.output = stream
                val param = writer.defaultWriteParam.apply {
                    compressionMode = ImageWriteParam.MODE_EXPLICIT
                    compressionQuality = JPEG_QUALITY
                    progressiveMode = ImageWriteParam.MODE_DEFAULT
                }
                writer.write(null, IIOImage(image, null, null), param)
            }
        } finally {
            writer.dispose()
        }
        return out.toByteArray()
    }
}
