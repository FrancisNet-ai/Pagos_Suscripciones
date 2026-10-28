package com.nuvyra.media

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class ThumbnailsTest {
    /** Imagen con ruido de color: comprime mal, como una foto real. */
    private fun photo(width: Int, height: Int, alpha: Boolean = false, format: String = "png"): ByteArray {
        val image = BufferedImage(width, height, if (alpha) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB)
        val random = java.util.Random(7)
        for (x in 0 until width) for (y in 0 until height) {
            image.setRGB(x, y, Color(random.nextInt(256), (x * 255) / width, (y * 255) / height, if (alpha) 128 else 255).rgb)
        }
        return ByteArrayOutputStream().also { ImageIO.write(image, format, it) }.toByteArray()
    }

    @Test
    fun `una foto grande se reduce a 640 px de ancho en JPEG y pesa menos`() {
        val original = photo(1600, 900)
        val small = Thumbnails.create(original, 640)!!
        assertThat(small.type).isEqualTo(ImageType.JPEG)
        assertThat(small.bytes.size).isLessThan(original.size)
        val decoded = ImageIO.read(small.bytes.inputStream())
        assertThat(decoded.width).isEqualTo(640)
        assertThat(decoded.height).isEqualTo(360)
    }

    @Test
    fun `una imagen con transparencia conserva PNG`() {
        val small = Thumbnails.create(photo(1200, 600, alpha = true), 640)!!
        assertThat(small.type).isEqualTo(ImageType.PNG)
        assertThat(ImageIO.read(small.bytes.inputStream()).colorModel.hasAlpha()).isTrue()
    }

    @Test
    fun `no se genera si no ahorra peso o el formato no se puede leer`() {
        val tiny = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", it) }.toByteArray()
        assertThat(Thumbnails.create(tiny, 640)).isNull()
        val webp = "RIFF".toByteArray() + ByteArray(4) + "WEBPVP8 ".toByteArray() + ByteArray(32)
        assertThat(Thumbnails.create(webp, 640)).isNull()
    }
}
