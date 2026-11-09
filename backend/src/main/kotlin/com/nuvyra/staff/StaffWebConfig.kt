package com.nuvyra.staff

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/** Primero se revisan los permisos del colaborador. */
@Configuration
class StaffWebConfig(private val interceptor: StaffAccessInterceptor) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(interceptor).addPathPatterns(StaffAccessInterceptor.PATHS).order(0)
    }
}
