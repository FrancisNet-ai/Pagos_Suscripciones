package com.nuvyra.saas

import com.nuvyra.businesses.BrandingGate
import org.springframework.stereotype.Component
import java.util.UUID

/** La personalización de la tienda depende de que el plan incluya [PlanFeature.BRANDING]. */
@Component
class PlanBrandingGate(private val entitlements: EntitlementService) : BrandingGate {
    override fun requireBranding(businessId: UUID) = entitlements.requireFeature(businessId, PlanFeature.BRANDING)
}
