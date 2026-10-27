package com.platform.common.persistence

import java.security.SecureRandom
import java.util.UUID

/** UUID versión 7 (RFC 9562): ordenable por tiempo, mejora la localidad de los índices. */
object UuidV7 {
    private val random = SecureRandom()

    fun generate(): UUID {
        val timestamp = System.currentTimeMillis()
        val randA = random.nextInt(1 shl 12).toLong()
        val randB = random.nextLong()
        val msb = (timestamp shl 16) or (0x7L shl 12) or randA
        val lsb = (randB and 0x3FFFFFFFFFFFFFFFL) or Long.MIN_VALUE
        return UUID(msb, lsb)
    }
}
