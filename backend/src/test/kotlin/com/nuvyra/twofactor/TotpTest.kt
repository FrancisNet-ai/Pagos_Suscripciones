package com.nuvyra.twofactor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TotpTest {
    // Vectores de la RFC 6238 (SHA-1, secreto ASCII "12345678901234567890"), truncados a 6 dígitos.
    private val secret = Totp.base32("12345678901234567890".toByteArray())

    @Test
    fun `coincide con los vectores de la RFC 6238`() {
        assertThat(secret).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")
        assertThat(Totp.code(secret, Totp.step(59))).isEqualTo("287082")
        assertThat(Totp.code(secret, Totp.step(1111111109))).isEqualTo("081804")
        assertThat(Totp.code(secret, Totp.step(1234567890))).isEqualTo("005924")
        assertThat(Totp.code(secret, Totp.step(2000000000))).isEqualTo("279037")
    }

    @Test
    fun `tolera un intervalo de desfase y rechaza codigos mal formados`() {
        val now = 1234567890L
        val step = Totp.step(now)
        assertThat(Totp.matchingStep(secret, Totp.code(secret, step - 1), now)).isEqualTo(step - 1)
        assertThat(Totp.matchingStep(secret, Totp.code(secret, step + 1), now)).isEqualTo(step + 1)
        assertThat(Totp.matchingStep(secret, Totp.code(secret, step + 2), now)).isNull()
        assertThat(Totp.matchingStep(secret, "12345", now)).isNull()
        assertThat(Totp.matchingStep(secret, "abcdef", now)).isNull()
    }

    @Test
    fun `base32 ida y vuelta y uri otpauth`() {
        val fresh = Totp.newSecret()
        assertThat(fresh).hasSize(32).matches("[A-Z2-7]+")
        assertThat(Totp.base32(Totp.decodeBase32(fresh))).isEqualTo(fresh)
        assertThat(Totp.otpauthUri("ABC", "Nuvyra", "ana@ejemplo.pe"))
            .isEqualTo("otpauth://totp/Nuvyra:ana%40ejemplo.pe?secret=ABC&issuer=Nuvyra&algorithm=SHA1&digits=6&period=30")
    }
}
