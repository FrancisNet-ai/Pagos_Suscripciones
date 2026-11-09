package com.nuvyra.security

import org.springframework.context.annotation.Configuration
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Solo registra lo propio de la seguridad. Cada módulo registra sus interceptores en su propia configuración
 * (staff, saas, twofactor), así quitar uno no obliga a tocar este archivo. Orden: permisos del colaborador (0),
 * plan del negocio (1) y verificación en dos pasos (2).
 */
@Configuration
class WebConfig(private val currentBusinessResolver: CurrentBusinessArgumentResolver) : WebMvcConfigurer {
    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(currentBusinessResolver)
    }
}
