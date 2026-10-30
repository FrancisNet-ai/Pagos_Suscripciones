package com.nuvyra.common.tenancy

import com.nuvyra.security.currentUserOrNull
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/** Negocio cuyas filas puede tocar el hilo actual (Row Level Security). `null`: sin restricción de la base. */
object TenantContext {
    private val current = ThreadLocal<UUID?>()

    fun get(): UUID? = current.get()

    fun <T> runAs(businessId: UUID?, block: () -> T): T {
        val previous = current.get()
        current.set(businessId)
        try {
            return block()
        } finally {
            if (previous == null) current.remove() else current.set(previous)
        }
    }
}

/**
 * Fija el negocio de la sesión en las peticiones del panel. Quedan fuera las rutas que resuelven el negocio
 * por sí mismas o cruzan negocios por diseño: autenticación, tienda y enlaces públicos, webhooks y
 * superadministración. Va después de [com.nuvyra.security.SessionAuthenticationFilter].
 */
class TenantContextFilter : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val businessId = currentUserOrNull()?.businessId?.takeIf { isPanelPath(request.requestURI.removePrefix(request.contextPath)) }
        TenantContext.runAs(businessId) { chain.doFilter(request, response) }
    }

    companion object {
        private val EXCLUDED = listOf("/api/v1/public/", "/api/v1/auth/", "/api/v1/admin/")

        fun isPanelPath(path: String) = path.startsWith("/api/v1/") && EXCLUDED.none { path.startsWith(it) } && path != "/api/v1/auth"
    }
}
