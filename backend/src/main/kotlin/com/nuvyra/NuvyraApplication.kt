package com.nuvyra

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration
import org.springframework.scheduling.annotation.EnableScheduling
import java.util.TimeZone

// Sin UserDetailsService: la autenticación usa sesiones propias (ver security/).
@SpringBootApplication(exclude = [UserDetailsServiceAutoConfiguration::class])
@ConfigurationPropertiesScan
@EnableScheduling
class NuvyraApplication

fun main(args: Array<String>) {
    // Todas las fechas se manejan en UTC; la zona de negocio se aplica explícitamente.
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    runApplication<NuvyraApplication>(*args)
}
