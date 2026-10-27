package com.nuvyra.common

import com.nuvyra.common.config.AppProperties
import com.nuvyra.common.error.ApiException
import com.nuvyra.common.text.SecretCipher
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class SecretCipherTest {
    private val cipher = SecretCipher(AppProperties(secrets = AppProperties.Secrets("dGVzdC1vbmx5LWVuY3J5cHRpb24ta2V5LTMyYnl0ZXM=")))

    @Test
    fun `cifra con IV aleatorio y descifra solo con el mismo contexto`() {
        val a = cipher.encrypt("EAAG-token", "negocio-1")
        val b = cipher.encrypt("EAAG-token", "negocio-1")
        assertThat(a).isNotEqualTo(b).startsWith("v1:").doesNotContain("EAAG")
        assertThat(cipher.decrypt(a, "negocio-1")).isEqualTo("EAAG-token")
        assertThatThrownBy { cipher.decrypt(a, "negocio-2") }.isInstanceOf(javax.crypto.AEADBadTagException::class.java)
    }

    @Test
    fun `sin clave configurada no guarda credenciales`() {
        val none = SecretCipher(AppProperties())
        assertThat(none.available).isFalse()
        assertThatThrownBy { none.encrypt("x", "y") }.isInstanceOf(ApiException::class.java)
    }
}
