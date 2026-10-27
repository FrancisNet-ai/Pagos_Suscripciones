package com.nuvyra.renewals

import com.nuvyra.common.config.AppProperties
import com.nuvyra.common.text.SecureTokens
import org.springframework.stereotype.Component
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Enlaces de renovación: `token = HMAC-SHA256(secreto, id de la solicitud)`. Es imposible de adivinar
 * sin el secreto, se puede reconstruir para cada aviso y en la base solo se guarda su SHA-256.
 * La URL no contiene teléfonos, correos ni otros datos personales.
 */
@Component
class RenewalTokens(properties: AppProperties) {
    private val key: SecretKeySpec

    init {
        val secret = properties.renewals.tokenSecret
        require(secret.length >= 32) { "APP_RENEWAL_TOKEN_SECRET debe tener al menos 32 caracteres" }
        key = SecretKeySpec(secret.toByteArray(), "HmacSHA256")
    }

    fun tokenFor(requestId: UUID): String {
        val mac = Mac.getInstance("HmacSHA256").apply { init(key) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(requestId.toString().toByteArray()))
    }

    fun hashFor(requestId: UUID): String = SecureTokens.sha256Hex(tokenFor(requestId))
}
