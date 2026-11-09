package com.nuvyra.twofactor

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/** Al final, la verificación en dos pasos obligatoria donde corresponda. */
@Configuration
class TwoFactorWebConfig(private val interceptor: TwoFactorEnforcementInterceptor) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(interceptor).addPathPatterns(TwoFactorEnforcementInterceptor.PATHS).order(2)
    }
}
