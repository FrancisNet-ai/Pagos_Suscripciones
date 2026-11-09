package com.nuvyra.businesses

import java.util.UUID

/**
 * Decide si un negocio puede personalizar su tienda (color y portada). La implementa el módulo de planes
 * (`saas`); este módulo no lo conoce, así que funciona igual si ese módulo no existe.
 */
fun interface BrandingGate {
    /** Lanza una excepción si el plan del negocio no incluye la personalización. */
    fun requireBranding(businessId: UUID)
}
