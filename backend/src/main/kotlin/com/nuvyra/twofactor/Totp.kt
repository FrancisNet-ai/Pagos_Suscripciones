package com.nuvyra.twofactor

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Códigos de un solo uso basados en tiempo (RFC 6238): HMAC-SHA1, 6 dígitos, intervalos de 30 s.
 * Es lo que esperan Google Authenticator, Microsoft Authenticator, 1Password y similares.
 */
object Totp {
    const val PERIOD_SECONDS = 30L
    private const val DIGITS = 6
    private const val SECRET_BYTES = 20
    private const val BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private val random = SecureRandom()

    /** Secreto nuevo de 160 bits en Base32, el formato que leen las apps de autenticación. */
    fun newSecret(): String = base32(ByteArray(SECRET_BYTES).also(random::nextBytes))

    fun step(epochSeconds: Long): Long = Math.floorDiv(epochSeconds, PERIOD_SECONDS)

    fun code(secret: String, step: Long): String {
        val mac = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(decodeBase32(secret), "HmacSHA1")) }
        val hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array())
        val offset = hash.last().toInt() and 0x0f
        val binary = ByteBuffer.wrap(hash, offset, 4).int and 0x7fffffff
        return (binary % 1_000_000).toString().padStart(DIGITS, '0')
    }

    /**
     * Devuelve el intervalo en el que coincide el código (tolera ±1 intervalo de desfase del reloj del
     * celular) o `null`. Quien llama rechaza intervalos ya usados para impedir la reutilización.
     */
    fun matchingStep(secret: String, code: String, epochSeconds: Long): Long? {
        if (code.length != DIGITS || !code.all(Char::isDigit)) return null
        val current = step(epochSeconds)
        return (current - 1..current + 1).firstOrNull { constantTimeEquals(code(secret, it), code) }
    }

    /** `otpauth://` que se muestra como QR; la app guarda la cuenta con emisor y correo. */
    fun otpauthUri(secret: String, issuer: String, account: String): String {
        fun enc(value: String) = java.net.URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")
        return "otpauth://totp/${enc(issuer)}:${enc(account)}?secret=$secret&issuer=${enc(issuer)}&algorithm=SHA1&digits=$DIGITS&period=$PERIOD_SECONDS"
    }

    fun base32(bytes: ByteArray): String = buildString {
        var buffer = 0
        var bits = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xff)
            bits += 8
            while (bits >= 5) {
                append(BASE32[(buffer shr (bits - 5)) and 31])
                bits -= 5
            }
        }
        if (bits > 0) append(BASE32[(buffer shl (5 - bits)) and 31])
    }

    fun decodeBase32(value: String): ByteArray {
        val clean = value.uppercase().filter { it != '=' && !it.isWhitespace() }
        val out = java.io.ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (c in clean) {
            val v = BASE32.indexOf(c)
            require(v >= 0) { "Carácter Base32 no válido" }
            buffer = (buffer shl 5) or v
            bits += 5
            if (bits >= 8) {
                out.write((buffer shr (bits - 8)) and 0xff)
                bits -= 8
            }
        }
        return out.toByteArray()
    }

    private fun constantTimeEquals(a: String, b: String) =
        java.security.MessageDigest.isEqual(a.toByteArray(), b.toByteArray())
}
