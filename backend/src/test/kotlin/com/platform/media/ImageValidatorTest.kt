package com.platform.media

import com.platform.common.error.ApiException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class ImageValidatorTest {
    private fun image(format: String, width: Int = 10, height: Int = 10) =
        ByteArrayOutputStream().also { ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, it) }.toByteArray()

    @Test
    fun `detecta PNG y JPEG por su contenido`() {
        assertThat(ImageValidator.validate(image("png"), 1_000_000, 100)).isEqualTo(ImageType.PNG)
        assertThat(ImageValidator.validate(image("jpg"), 1_000_000, 100)).isEqualTo(ImageType.JPEG)
    }

    @Test
    fun `detecta WebP por su cabecera RIFF`() {
        val header = "RIFF".toByteArray() + byteArrayOf(0, 0, 0, 0) + "WEBPVP8 ".toByteArray()
        assertThat(ImageValidator.detect(header)).isEqualTo(ImageType.WEBP)
    }

    @Test
    fun `rechaza archivos que no son imagenes aunque se llamen png`() {
        assertThatThrownBy { ImageValidator.validate("<svg onload=alert(1)>".toByteArray(), 1_000_000, 100) }
            .isInstanceOf(ApiException::class.java).hasMessageContaining("Formato no permitido")
    }

    @Test
    fun `rechaza imagenes demasiado pesadas o grandes`() {
        assertThatThrownBy { ImageValidator.validate(image("png"), 10, 100) }.hasMessageContaining("tamaño máximo")
        assertThatThrownBy { ImageValidator.validate(image("png", 200, 20), 1_000_000, 100) }.hasMessageContaining("píxeles")
    }

    @Test
    fun `rechaza una imagen truncada`() {
        val truncated = image("png").copyOfRange(0, 20)
        assertThatThrownBy { ImageValidator.validate(truncated, 1_000_000, 100) }.isInstanceOf(ApiException::class.java)
    }
}
