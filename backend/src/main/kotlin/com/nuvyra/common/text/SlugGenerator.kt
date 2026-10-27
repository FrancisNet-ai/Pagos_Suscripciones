package com.nuvyra.common.text

import java.text.Normalizer

object SlugGenerator {
    const val MAX_LENGTH = 50
    private const val FALLBACK = "tienda"
    private val diacritics = "\\p{M}+".toRegex()
    private val nonAlphanumeric = "[^a-z0-9]+".toRegex()

    /** Palabras que no deben usarse como slug público para evitar confusiones o suplantación. */
    val reserved = setOf(
        "admin", "administrador", "api", "panel", "soporte", "ayuda", "www", "app",
        "ingresar", "registro", "login", "tienda", "renovar", "pedido", "plataforma", "nuvyra",
    )

    fun slugify(input: String): String {
        val ascii = Normalizer.normalize(input.lowercase(), Normalizer.Form.NFD)
            .replace(diacritics, "")
            .replace("ñ", "n")
        val slug = ascii.replace(nonAlphanumeric, "-").trim('-').take(MAX_LENGTH).trimEnd('-')
        return if (slug.length < 3 || slug in reserved) "$FALLBACK-${slug.ifEmpty { "nueva" }}".take(MAX_LENGTH).trimEnd('-') else slug
    }
}
