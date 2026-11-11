package com.nuvyra.businesses

import com.nuvyra.common.error.ApiException
import java.net.URI

/** Enlaces a las redes del vendedor que se muestran como íconos en su tienda. */
data class SocialLinks(
    val instagram: String? = null,
    val facebook: String? = null,
    val tiktok: String? = null,
    val website: String? = null,
) {
    companion object {
        fun of(b: Business) = SocialLinks(b.instagramUrl, b.facebookUrl, b.tiktokUrl, b.websiteUrl)

        private val INSTAGRAM = setOf("instagram.com")
        private val FACEBOOK = setOf("facebook.com", "fb.com", "fb.me")
        private val TIKTOK = setOf("tiktok.com")
        private val HANDLE = Regex("^@?([A-Za-z0-9._]{1,40})$")

        fun instagram(raw: String?) = normalize(raw, "instagramUrl", "Instagram", INSTAGRAM) { "https://www.instagram.com/$it" }
        fun facebook(raw: String?) = normalize(raw, "facebookUrl", "Facebook", FACEBOOK, handleUrl = null)
        fun tiktok(raw: String?) = normalize(raw, "tiktokUrl", "TikTok", TIKTOK) { "https://www.tiktok.com/@$it" }
        fun website(raw: String?) = normalize(raw, "websiteUrl", "tu página web", allowedHosts = null, handleUrl = null)

        /**
         * Acepta el enlace completo, sin «https://» o, en Instagram y TikTok, el @usuario. Siempre devuelve https y
         * solo del dominio de esa red: así un ícono de Instagram nunca lleva a otro sitio.
         */
        private fun normalize(
            raw: String?,
            field: String,
            label: String,
            allowedHosts: Set<String>?,
            handleUrl: ((String) -> String)?,
        ): String? {
            val value = raw?.trim().orEmpty()
            if (value.isEmpty()) return null
            // «@usuario» (o «usuario» sin puntos ni barras) es un usuario de la red, no un dominio.
            if (handleUrl != null && !value.contains('/') && (value.startsWith('@') || !value.contains('.'))) {
                HANDLE.matchEntire(value)?.let { return handleUrl(it.groupValues[1]) }
            }
            val withScheme = if (value.startsWith("http://", true) || value.startsWith("https://", true)) value else "https://$value"
            val uri = runCatching { URI(withScheme) }.getOrNull()
            val host = uri?.host?.lowercase()?.removePrefix("www.")?.removePrefix("m.")?.removePrefix("vm.")
            val invalid = ApiException.invalidField(field, "Ingresa un enlace válido de $label")
            if (uri == null || host == null || uri.userInfo != null || !host.contains('.') || host == "localhost") throw invalid
            if (allowedHosts != null && allowedHosts.none { host == it || host.endsWith(".$it") }) throw invalid
            val https = URI("https", null, uri.host.lowercase(), uri.port, uri.path?.ifEmpty { "/" } ?: "/", uri.query, null).toString()
            if (https.length > 300) throw ApiException.invalidField(field, "El enlace es demasiado largo")
            return https
        }
    }
}
