package com.nuvyra.security

import com.nuvyra.common.config.AppProperties
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant

@Component
class SessionCookies(private val properties: AppProperties, private val clock: Clock) {
    val name: String get() = properties.session.cookieName

    fun read(request: HttpServletRequest): String? =
        request.cookies?.firstOrNull { it.name == name }?.value?.takeIf { it.isNotBlank() && it.length <= 128 }

    fun write(response: HttpServletResponse, token: String, expiresAt: Instant) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(token, Duration.between(clock.instant(), expiresAt)).toString())
    }

    fun clear(response: HttpServletResponse) {
        response.addHeader(HttpHeaders.SET_COOKIE, build("", Duration.ZERO).toString())
    }

    private fun build(value: String, maxAge: Duration) = ResponseCookie.from(name, value)
        .httpOnly(true)
        .secure(properties.session.cookieSecure)
        .sameSite("Lax")
        .path("/")
        .maxAge(maxAge)
        .build()
}
