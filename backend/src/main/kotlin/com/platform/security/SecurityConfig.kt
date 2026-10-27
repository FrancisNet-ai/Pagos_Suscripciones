package com.platform.security

import com.platform.common.config.AppProperties
import com.platform.common.error.ProblemDetails
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.csrf.CsrfException
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import tools.jackson.databind.ObjectMapper

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val properties: AppProperties,
    private val objectMapper: ObjectMapper,
) {

    @Bean
    fun passwordEncoder(): PasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()

    @Bean
    fun securityFilterChain(http: HttpSecurity, sessionService: SessionService, cookies: SessionCookies): SecurityFilterChain {
        http
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .logout { it.disable() }
            .requestCache { it.disable() }
            .csrf { csrf ->
                // Double submit cookie: Angular lee XSRF-TOKEN y envía X-XSRF-TOKEN.
                csrf.spa()
                csrf.csrfTokenRepository(
                    CookieCsrfTokenRepository.withHttpOnlyFalse().apply {
                        setCookieCustomizer { it.secure(properties.session.cookieSecure).sameSite("Lax").path("/") }
                    },
                )
            }
            .cors { cors ->
                if (properties.cors.allowedOrigins.isEmpty()) {
                    cors.disable()
                } else {
                    cors.configurationSource(
                        UrlBasedCorsConfigurationSource().apply {
                            registerCorsConfiguration(
                                "/api/**",
                                CorsConfiguration().apply {
                                    allowedOrigins = properties.cors.allowedOrigins
                                    allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE")
                                    allowedHeaders = listOf("Content-Type", "X-XSRF-TOKEN", "Idempotency-Key")
                                    allowCredentials = true
                                    maxAge = 3600
                                },
                            )
                        },
                    )
                }
            }
            .headers { headers ->
                headers.frameOptions { it.deny() }
                headers.referrerPolicy { it.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN) }
                headers.httpStrictTransportSecurity { it.includeSubDomains(true).maxAgeInSeconds(31_536_000) }
            }
            .addFilterBefore(SessionAuthenticationFilter(sessionService, cookies), AnonymousAuthenticationFilter::class.java)
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/password-reset", "/api/v1/auth/password-reset/confirm").permitAll()
                    .requestMatchers("/api/v1/public/**").permitAll()
                    .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                    .requestMatchers("/api/docs", "/api/docs/**", "/swagger-ui/**").permitAll()
                    .requestMatchers("/api/v1/admin/**").hasRole(Roles.SUPER_ADMIN)
                    .requestMatchers("/api/v1/business/**", "/api/v1/products/**", "/api/v1/orders/**", "/api/v1/payments/**",
                        "/api/v1/customers/**", "/api/v1/subscriptions/**", "/api/v1/reports/**",
                    )
                    .hasRole(Roles.BUSINESS_OWNER)
                    .requestMatchers("/error").permitAll()
                    .anyRequest().authenticated()
            }
            .exceptionHandling { ex ->
                ex.authenticationEntryPoint { _, response, _ ->
                    writeProblem(response, ProblemDetails.of(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Debes iniciar sesión."))
                }
                ex.accessDeniedHandler { _, response, exception ->
                    val problem = if (exception is CsrfException) {
                        ProblemDetails.of(HttpStatus.FORBIDDEN, "CSRF_INVALID", "La sesión del formulario expiró. Recarga la página.")
                    } else {
                        ProblemDetails.of(HttpStatus.FORBIDDEN, "FORBIDDEN", "No tienes permiso para realizar esta acción.")
                    }
                    writeProblem(response, problem)
                }
            }
        return http.build()
    }

    private fun writeProblem(response: HttpServletResponse, problem: ProblemDetail) {
        response.status = problem.status
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        response.characterEncoding = "UTF-8"
        objectMapper.writeValue(response.outputStream, problem)
    }
}
