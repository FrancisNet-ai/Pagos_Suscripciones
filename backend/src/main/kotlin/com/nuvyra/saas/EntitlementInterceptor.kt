package com.nuvyra.saas

import com.nuvyra.security.currentUserOrNull
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

/**
 * Sin plan vigente (o con el negocio suspendido) el panel queda en solo lectura. Las rutas excluidas
 * en [com.nuvyra.security.WebConfig] siguen disponibles: Mi plan y lo que se le debe a compradores
 * que ya pagaron (verificar o rechazar pagos, activar o cancelar su servicio, liberar cupos, exclusiones).
 */
@Component
class EntitlementInterceptor(private val entitlements: EntitlementService) : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (request.method in SAFE_METHODS) return true
        val businessId = currentUserOrNull()?.businessId ?: return true
        entitlements.requireActive(businessId)
        return true
    }

    companion object {
        private val SAFE_METHODS = setOf("GET", "HEAD", "OPTIONS")

        val PATHS = listOf(
            "/api/v1/business/**", "/api/v1/products/**", "/api/v1/orders/**", "/api/v1/payments/**",
            "/api/v1/customers/**", "/api/v1/subscriptions/**", "/api/v1/reports/**", "/api/v1/renewals/**",
            "/api/v1/tasks/**",
        )

        val EXEMPT = listOf(
            "/api/v1/payments/*/verify", "/api/v1/payments/*/reject",
            "/api/v1/orders/*/cancel",
            "/api/v1/subscriptions/*/activate", "/api/v1/subscriptions/*/cancel", "/api/v1/subscriptions/*/release-capacity",
            "/api/v1/customers/*/notifications",
            // Quitar accesos del equipo es una medida de seguridad: siempre debe poder hacerse.
            "/api/v1/business/me/staff/*/revoke", "/api/v1/business/me/staff/invitations/*",
            // Desconectar WhatsApp borra credenciales: siempre debe poder hacerse.
            "/api/v1/business/me/whatsapp",
        )
    }
}
