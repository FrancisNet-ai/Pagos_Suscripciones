package com.platform.common.text

import com.platform.common.config.AppProperties
import com.platform.common.error.ApiException
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cifrado AES-256-GCM para credenciales de terceros (p. ej. tokens de WhatsApp). El contexto
 * (`aad`, normalmente el id del negocio) se autentica: un valor copiado a otra fila no se descifra.
 * Formato: `v1:` + Base64(iv || texto cifrado || etiqueta).
 */
@Component
class SecretCipher(properties: AppProperties) {
    private val key: SecretKeySpec? = properties.secrets.encryptionKey.takeIf { it.isNotBlank() }?.let { raw ->
        val bytes = Base64.getDecoder().decode(raw.trim())
        require(bytes.size == 32) { "APP_SECRETS_ENCRYPTION_KEY debe ser una clave de 32 bytes en Base64" }
        SecretKeySpec(bytes, "AES")
    }
    private val random = SecureRandom()

    val available: Boolean get() = key != null

    fun encrypt(plain: String, aad: String): String {
        val k = requireKey()
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, k, GCMParameterSpec(TAG_BITS, iv))
            updateAAD(aad.toByteArray())
        }
        return PREFIX + Base64.getEncoder().encodeToString(iv + cipher.doFinal(plain.toByteArray()))
    }

    fun decrypt(encoded: String, aad: String): String {
        val k = requireKey()
        require(encoded.startsWith(PREFIX)) { "Formato de secreto desconocido" }
        val bytes = Base64.getDecoder().decode(encoded.removePrefix(PREFIX))
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
            updateAAD(aad.toByteArray())
        }
        return String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES))
    }

    private fun requireKey(): SecretKeySpec = key ?: throw ApiException.conflict(
        "ENCRYPTION_NOT_CONFIGURED", "La plataforma aún no tiene configurado el cifrado de credenciales. Escribe a soporte.",
    )

    private companion object {
        const val PREFIX = "v1:"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
