package com.nuvyra.common

import com.nuvyra.common.text.SlugGenerator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SlugGeneratorTest {
    @Test
    fun `quita tildes, enes y simbolos`() {
        assertThat(SlugGenerator.slugify("Digital Streaming Perú")).isEqualTo("digital-streaming-peru")
        assertThat(SlugGenerator.slugify("  Señor   Café & Té!! ")).isEqualTo("senor-cafe-te")
    }

    @Test
    fun `limita la longitud sin guion final`() {
        val slug = SlugGenerator.slugify("a".repeat(49) + " bbbbbb")
        assertThat(slug.length).isLessThanOrEqualTo(SlugGenerator.MAX_LENGTH)
        assertThat(slug).doesNotEndWith("-")
    }

    @Test
    fun `evita slugs reservados o demasiado cortos`() {
        assertThat(SlugGenerator.slugify("Admin")).isEqualTo("tienda-admin")
        assertThat(SlugGenerator.slugify("!!")).isEqualTo("tienda-nueva")
        assertThat(SlugGenerator.slugify("AB")).isEqualTo("tienda-ab")
    }

    @Test
    fun `cumple el formato exigido por la base de datos`() {
        listOf("Ñandú Digital", "123 Streaming", "Edu---Digital", "ÁÉÍÓÚ").forEach {
            assertThat(SlugGenerator.slugify(it)).matches("^[a-z0-9]+(-[a-z0-9]+)*$")
        }
    }
}
