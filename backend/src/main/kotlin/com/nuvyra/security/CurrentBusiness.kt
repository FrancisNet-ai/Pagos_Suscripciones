package com.nuvyra.security

import com.nuvyra.businesses.BusinessRole
import com.nuvyra.businesses.StaffPermission
import com.nuvyra.common.error.ApiException
import org.springframework.core.MethodParameter
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import java.util.UUID

/**
 * Negocio sobre el que actúa el usuario autenticado. Se obtiene SIEMPRE de la sesión y de
 * `business_members`, nunca de datos enviados por el cliente.
 */
data class CurrentBusiness(
    val businessId: UUID,
    val userId: UUID,
    val role: BusinessRole,
    val permissions: Set<StaffPermission> = emptySet(),
) {
    val isOwner: Boolean get() = role == BusinessRole.OWNER
}

/** Usuario autenticado inyectable en controladores. */
fun currentUserOrNull(): AuthenticatedUser? =
    (SecurityContextHolder.getContext().authentication as? SessionAuthentication)?.user

fun currentUser(): AuthenticatedUser = currentUserOrNull() ?: throw ApiException.unauthorized()

@Component
class CurrentBusinessArgumentResolver : HandlerMethodArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter) =
        parameter.parameterType == CurrentBusiness::class.java || parameter.parameterType == AuthenticatedUser::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): Any {
        val user = currentUser()
        if (parameter.parameterType == AuthenticatedUser::class.java) return user
        val businessId = user.businessId ?: throw ApiException.forbidden("Tu cuenta no está asociada a ningún negocio.")
        return CurrentBusiness(businessId, user.userId, user.businessRole!!, user.permissions)
    }
}
