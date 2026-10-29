package com.nuvyra.twofactor

import com.nuvyra.common.error.ApiException
import com.nuvyra.security.currentUserOrNull
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

/** Sin verificación en dos pasos, el superadministrador no puede usar `/api/v1/admin` (solo «Mi cuenta»). */
@Component
class TwoFactorEnforcementInterceptor(private val twoFactor: TwoFactorService) : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val user = currentUserOrNull() ?: return true
        if (twoFactor.required(user.roles) && !user.twoFactorEnabled) {
            throw ApiException(
                HttpStatus.FORBIDDEN, "TWO_FACTOR_REQUIRED",
                "Activa la verificación en dos pasos en «Mi cuenta» para usar la administración.",
            )
        }
        return true
    }

    companion object {
        const val PATHS = "/api/v1/admin/**"
    }
}
