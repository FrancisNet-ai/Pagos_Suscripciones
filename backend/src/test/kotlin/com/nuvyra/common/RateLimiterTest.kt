package com.nuvyra.common

import com.nuvyra.common.error.ApiException
import com.nuvyra.common.ratelimit.RateLimiter
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration

class RateLimiterTest {
    private var now = 1_000_000L
    private val clock = mockk<Clock> { every { millis() } answers { now } }
    private val limiter = RateLimiter(clock)

    @Test
    fun `permite hasta el limite dentro de la ventana`() {
        repeat(3) { assertThat(limiter.tryAcquire("k", 3, Duration.ofMinutes(1))).isTrue() }
        assertThat(limiter.tryAcquire("k", 3, Duration.ofMinutes(1))).isFalse()
        assertThatThrownBy { limiter.check("k", 3, Duration.ofMinutes(1)) }
            .isInstanceOf(ApiException::class.java)
    }

    @Test
    fun `reinicia el contador al terminar la ventana`() {
        repeat(3) { limiter.tryAcquire("k", 3, Duration.ofMinutes(1)) }
        now += Duration.ofMinutes(1).toMillis()
        assertThat(limiter.tryAcquire("k", 3, Duration.ofMinutes(1))).isTrue()
    }

    @Test
    fun `las claves son independientes`() {
        repeat(3) { limiter.tryAcquire("a", 3, Duration.ofMinutes(1)) }
        assertThat(limiter.tryAcquire("b", 3, Duration.ofMinutes(1))).isTrue()
    }
}
