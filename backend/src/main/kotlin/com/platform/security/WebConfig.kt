package com.platform.security

import com.platform.saas.EntitlementInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig(
    private val currentBusinessResolver: CurrentBusinessArgumentResolver,
    private val entitlementInterceptor: EntitlementInterceptor,
) : WebMvcConfigurer {
    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(currentBusinessResolver)
    }

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(entitlementInterceptor)
            .addPathPatterns(EntitlementInterceptor.PATHS)
            .excludePathPatterns(EntitlementInterceptor.EXEMPT)
    }
}
