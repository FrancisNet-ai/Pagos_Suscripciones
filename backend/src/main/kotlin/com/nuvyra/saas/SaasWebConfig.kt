package com.nuvyra.saas

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/** Después de los permisos del colaborador, se exige un plan vigente. */
@Configuration
class SaasWebConfig(private val interceptor: EntitlementInterceptor) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(interceptor)
            .addPathPatterns(EntitlementInterceptor.PATHS)
            .excludePathPatterns(EntitlementInterceptor.EXEMPT)
            .order(1)
    }
}
