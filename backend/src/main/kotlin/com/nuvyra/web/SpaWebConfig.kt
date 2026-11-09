package com.nuvyra.web

import com.nuvyra.common.config.AppProperties
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.Resource
import org.springframework.http.CacheControl
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.resource.PathResourceResolver
import java.time.Duration

/**
 * Sirve la aplicación Angular desde el mismo backend cuando `app.web.static-dir` está definido (despliegue en un
 * solo servicio). Hace lo mismo que la configuración de Nginx de Docker Compose:
 * - los archivos con hash en el nombre se guardan en caché un año; `index.html`, nunca;
 * - las rutas de la aplicación (`/panel/ventas`, `/tienda/x`…) devuelven `index.html` para que las resuelva Angular;
 * - las mismas cabeceras de seguridad (CSP, Permissions-Policy) que `security-headers.conf`.
 */
@Configuration
class SpaWebConfig(private val properties: AppProperties) : WebMvcConfigurer {
    private val location: String? = properties.web.staticDir.takeIf { it.isNotBlank() }?.let { "file:" + it.trimEnd('/') + "/" }

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        val root = location ?: return
        registry.addResourceHandler("/*.js", "/*.css")
            .addResourceLocations(root)
            .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
        registry.addResourceHandler("/media/**")
            .addResourceLocations(root + "media/")
            .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
        registry.addResourceHandler("/art/**")
            .addResourceLocations(root + "art/")
            .setCacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic().staleWhileRevalidate(Duration.ofDays(1)))
        registry.addResourceHandler("/loading-mark.svg", "/icon.svg", "/favicon.ico")
            .addResourceLocations(root)
            // Sin hash: renovar periódicamente para que una nueva identidad pueda publicarse.
            .setCacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic().staleWhileRevalidate(Duration.ofDays(1)))
        registry.addResourceHandler("/**")
            .addResourceLocations(root)
            .setCacheControl(CacheControl.noCache())
            .resourceChain(true)
            .addResolver(SpaFallbackResolver())
    }

    /** La raíz no pasa por el manejador de recursos: se reenvía a `index.html`. */
    override fun addViewControllers(registry: ViewControllerRegistry) {
        if (location != null) registry.addViewController("/").setViewName("forward:/index.html")
    }

    @Bean
    fun spaSecurityHeaders(): FilterRegistrationBean<OncePerRequestFilter> = FilterRegistrationBean<OncePerRequestFilter>(
        object : OncePerRequestFilter() {
            override fun shouldNotFilter(request: HttpServletRequest) = location == null || request.requestURI.startsWith("/api/")

            override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
                response.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY)
                response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()")
                chain.doFilter(request, response)
            }
        },
    )

    /** Un archivo que existe se sirve tal cual; una ruta de la aplicación (sin extensión) devuelve `index.html`. */
    private class SpaFallbackResolver : PathResourceResolver() {
        override fun getResource(resourcePath: String, location: Resource): Resource? {
            val requested = location.createRelative(resourcePath)
            if (requested.exists() && requested.isReadable) return requested
            val isAppRoute = !resourcePath.startsWith("api/") && !resourcePath.startsWith("actuator") &&
                !resourcePath.substringAfterLast('/').contains('.')
            return if (isAppRoute) location.createRelative("index.html").takeIf { it.exists() } else null
        }
    }

    companion object {
        /** Igual que `infrastructure/docker/nginx/security-headers.conf`. */
        const val CONTENT_SECURITY_POLICY = "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; " +
            "img-src 'self' data: blob:; font-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; " +
            "form-action 'self'; object-src 'none'"
    }
}
