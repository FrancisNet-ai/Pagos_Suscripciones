package com.nuvyra.security

import com.nuvyra.saas.EntitlementInterceptor
import com.nuvyra.staff.StaffAccessInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig(
    private val currentBusinessResolver: CurrentBusinessArgumentResolver,
    private val entitlementInterceptor: EntitlementInterceptor,
    private val staffAccessInterceptor: StaffAccessInterceptor,
) : WebMvcConfigurer {
    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(currentBusinessResolver)
    }

    override fun addInterceptors(registry: InterceptorRegistry) {
        // Primero los permisos del colaborador; luego el plan del negocio.
        registry.addInterceptor(staffAccessInterceptor).addPathPatterns(StaffAccessInterceptor.PATHS).order(0)
        registry.addInterceptor(entitlementInterceptor)
            .addPathPatterns(EntitlementInterceptor.PATHS)
            .excludePathPatterns(EntitlementInterceptor.EXEMPT)
            .order(1)
    }
}
