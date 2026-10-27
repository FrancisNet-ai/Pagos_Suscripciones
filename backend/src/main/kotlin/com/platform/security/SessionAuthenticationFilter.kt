package com.platform.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/** Autentica la petición a partir de la cookie de sesión. Si no es válida, la petición sigue como anónima. */
class SessionAuthenticationFilter(
    private val sessionService: SessionService,
    private val cookies: SessionCookies,
) : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val token = cookies.read(request)
        if (token != null) {
            val user = sessionService.authenticate(token)
            if (user != null) {
                val context = SecurityContextHolder.createEmptyContext()
                context.authentication = SessionAuthentication(user)
                SecurityContextHolder.setContext(context)
            } else {
                // Cookie caducada o revocada: se elimina para que el navegador deje de enviarla.
                cookies.clear(response)
            }
        }
        try {
            chain.doFilter(request, response)
        } finally {
            SecurityContextHolder.clearContext()
        }
    }
}
