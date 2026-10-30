package com.nuvyra.security

import com.nuvyra.common.config.AppProperties
import com.nuvyra.common.error.ApiException
import org.slf4j.LoggerFactory
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.security.MessageDigest
import java.util.HexFormat

/**
 * Rechaza contraseñas que aparecen en filtraciones conocidas, con la API Pwned Passwords y k-anonimato:
 * solo viajan los 5 primeros caracteres del SHA-1 y la comparación se hace aquí. La contraseña nunca sale
 * del servidor. Si el servicio no responde se acepta la contraseña (no bloquea registros ni recuperaciones).
 */
@Component
class BreachedPasswordChecker(private val properties: AppProperties) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val client: RestClient = RestClient.builder()
        .baseUrl(properties.security.pwnedPasswordsUrl.trimEnd('/'))
        .requestFactory(
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(properties.security.breachedPasswordTimeout)
                setReadTimeout(properties.security.breachedPasswordTimeout)
            },
        )
        .build()

    /** Lanza un error de validación en `field` si la contraseña está filtrada. */
    fun ensureNotBreached(password: String, field: String) {
        if (timesBreached(password)?.let { it > 0 } == true) {
            throw ApiException.invalidField(
                field, "Esta contraseña apareció en filtraciones de datos de otros sitios. Elige una distinta que no uses en otro lugar.",
            )
        }
    }

    /** Veces que aparece en filtraciones; `null` si la comprobación está apagada o el servicio no respondió. */
    fun timesBreached(password: String): Int? {
        if (!properties.security.breachedPasswordCheck) return null
        val hash = HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-1").digest(password.toByteArray()))
        val prefix = hash.take(5)
        val suffix = hash.drop(5)
        val body = runCatching {
            // El relleno hace que todas las respuestas pesen parecido: quien observe el tráfico no deduce nada.
            client.get().uri("/range/{prefix}", prefix).header("Add-Padding", "true").retrieve().body(String::class.java)
        }.onFailure { log.warn("No se pudo consultar Pwned Passwords: {}", it.message) }.getOrNull() ?: return null
        return body.lineSequence()
            .map { it.trim().split(':') }
            .firstOrNull { it.size == 2 && it[0].equals(suffix, ignoreCase = true) }
            ?.get(1)?.toIntOrNull()
            ?: 0
    }
}
