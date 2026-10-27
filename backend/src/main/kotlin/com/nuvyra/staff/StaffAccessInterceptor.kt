package com.nuvyra.staff

import com.nuvyra.businesses.BusinessRole
import com.nuvyra.businesses.StaffPermission
import com.nuvyra.common.error.ApiException
import com.nuvyra.security.currentUserOrNull
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.util.AntPathMatcher
import org.springframework.web.servlet.HandlerInterceptor

/**
 * Permisos de los colaboradores, validados en el servidor para cada petición. El titular pasa siempre.
 * Las reglas van de la más específica a la más general; una ruta sin regla es exclusiva del titular.
 */
@Component
class StaffAccessInterceptor : HandlerInterceptor {
    private val matcher = AntPathMatcher()

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val user = currentUserOrNull() ?: return true
        if (user.businessRole != BusinessRole.STAFF) return true
        val path = request.requestURI.removePrefix(request.contextPath)
        if (request.method != "GET" && OWNER_ONLY.any { matcher.match(it, path) }) throw ApiException(HttpStatus.FORBIDDEN, "STAFF_FORBIDDEN", OWNER_MESSAGE)
        val rule = RULES.firstOrNull { (it.method == null || it.method == request.method) && matcher.match(it.pattern, path) }
        if (rule == null) throw ApiException(HttpStatus.FORBIDDEN, "STAFF_FORBIDDEN", OWNER_MESSAGE)
        if (rule.anyOf.isNotEmpty() && rule.anyOf.none { it in user.permissions }) {
            throw ApiException(
                HttpStatus.FORBIDDEN, "STAFF_FORBIDDEN",
                "No tienes acceso a «${rule.anyOf.first().label}». Pídeselo al titular del negocio.",
            )
        }
        return true
    }

    /** Sin permisos en [anyOf], basta con ser miembro activo del negocio. */
    private class Rule(val pattern: String, val method: String?, vararg val anyOf: StaffPermission)

    companion object {
        // De la más específica a la más general; gana la primera que coincide.
        private val RULES = listOf(
            // Datos públicos del negocio (nombre, enlace) que el panel necesita para cualquier miembro.
            Rule("/api/v1/business/me", "GET"),
            // Elegir el producto al registrar una suscripción manual.
            Rule("/api/v1/products", "GET", StaffPermission.PRODUCTS, StaffPermission.CUSTOMERS),
            Rule("/api/v1/products/**", null, StaffPermission.PRODUCTS),
            Rule("/api/v1/orders/**", null, StaffPermission.ORDERS),
            Rule("/api/v1/payments/**", null, StaffPermission.ORDERS),
            // Renovaciones lista y libera los cupos de suscripciones vencidas.
            Rule("/api/v1/subscriptions", "GET", StaffPermission.CUSTOMERS, StaffPermission.RENEWALS),
            Rule("/api/v1/subscriptions/*/release-capacity", "POST", StaffPermission.CUSTOMERS, StaffPermission.RENEWALS),
            Rule("/api/v1/subscriptions/**", null, StaffPermission.CUSTOMERS),
            Rule("/api/v1/customers/**", null, StaffPermission.CUSTOMERS),
            Rule("/api/v1/renewals/**", null, StaffPermission.RENEWALS),
            Rule("/api/v1/reports/**", null, StaffPermission.REPORTS),
        )

        /** Cambios siempre del titular, aunque el colaborador tenga la sección: la política de avisos del negocio. */
        private val OWNER_ONLY = listOf("/api/v1/renewals/settings/**")

        private const val OWNER_MESSAGE = "Esta sección es exclusiva del titular del negocio."

        // Rutas a las que se aplica; lo demás del negocio queda para el titular.
        val PATHS = listOf(
            "/api/v1/business/**", "/api/v1/products/**", "/api/v1/orders/**", "/api/v1/payments/**",
            "/api/v1/customers/**", "/api/v1/subscriptions/**", "/api/v1/reports/**", "/api/v1/renewals/**",
        )
    }
}
