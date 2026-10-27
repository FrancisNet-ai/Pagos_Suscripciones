package com.nuvyra.common.ratelimit

import com.nuvyra.common.error.ApiException
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * Limitador de ventana fija en memoria. Suficiente para una instancia (ver ADR-001);
 * con varias instancias debe sustituirse por un almacenamiento compartido.
 */
@Component
class RateLimiter(private val clock: Clock) {
    private data class Window(val startedAt: Long, val windowMillis: Long, var count: Int)

    private val windows = ConcurrentHashMap<String, Window>()

    /** Registra un intento; devuelve `false` si se superó el límite. */
    fun tryAcquire(key: String, limit: Int, window: Duration): Boolean {
        val now = clock.millis()
        val windowMillis = window.toMillis()
        var allowed = false
        windows.compute(key) { _, current ->
            val active = if (current == null || now - current.startedAt >= current.windowMillis) Window(now, windowMillis, 0) else current
            allowed = active.count < limit
            if (allowed) active.count++
            active
        }
        return allowed
    }

    fun check(key: String, limit: Int, window: Duration) {
        if (!tryAcquire(key, limit, window)) throw ApiException.rateLimited()
    }

    fun reset(key: String) {
        windows.remove(key)
    }

    /** Solo para pruebas. */
    fun clearAll() {
        windows.clear()
    }

    @Scheduled(fixedDelayString = "PT5M")
    fun evictExpired() {
        val now = clock.millis()
        windows.entries.removeIf { now - it.value.startedAt >= it.value.windowMillis }
    }
}
