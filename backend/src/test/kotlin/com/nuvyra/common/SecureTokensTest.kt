package com.nuvyra.common

import com.nuvyra.common.persistence.UuidV7
import com.nuvyra.common.text.SecureTokens
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SecureTokensTest {
    @Test
    fun `tokens son unicos, URL-safe y de 256 bits`() {
        val tokens = (1..1000).map { SecureTokens.newToken() }.toSet()
        assertThat(tokens).hasSize(1000)
        tokens.forEach { assertThat(it).matches("^[A-Za-z0-9_-]{43}$") }
    }

    @Test
    fun `el hash es determinista y no revela el token`() {
        val token = SecureTokens.newToken()
        assertThat(SecureTokens.sha256Hex(token)).isEqualTo(SecureTokens.sha256Hex(token)).hasSize(64).doesNotContain(token)
    }

    @Test
    fun `codigos legibles evitan caracteres ambiguos`() {
        repeat(200) { assertThat(SecureTokens.readableCode(8)).matches("^[0-9A-HJKMNP-TV-Z]{8}$") }
    }

    @Test
    fun `UUID v7 tiene version y variante correctas y es ordenable en el tiempo`() {
        val first = UuidV7.generate()
        Thread.sleep(2)
        val second = UuidV7.generate()
        assertThat(first.version()).isEqualTo(7)
        assertThat(first.variant()).isEqualTo(2)
        assertThat(first.toString() < second.toString()).isTrue()
    }
}
