package com.platform.common.text

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.HexFormat

/** Generación y hash de tokens opacos (sesiones, recuperación, renovaciones). */
object SecureTokens {
    private val random = SecureRandom()
    private const val CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    /** Token de 256 bits codificado en Base64 URL-safe sin relleno. */
    fun newToken(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun sha256Hex(value: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.toByteArray()))

    /** Código legible sin caracteres ambiguos (I, L, O, U). */
    fun readableCode(length: Int): String =
        buildString(length) { repeat(length) { append(CROCKFORD[random.nextInt(CROCKFORD.length)]) } }
}
