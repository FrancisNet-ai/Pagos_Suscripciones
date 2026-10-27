package com.platform.common.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {
    @Bean
    fun openApi(): OpenAPI = OpenAPI().info(
        Info()
            .title("Plataforma SaaS multiempresa — API v1")
            .version("v1")
            .description(
                "Autenticación por cookie de sesión HttpOnly. Las peticiones que modifican estado " +
                    "requieren el header X-XSRF-TOKEN con el valor de la cookie XSRF-TOKEN.",
            ),
    )
}
