package com.nuvyra.security

import com.nuvyra.businesses.BusinessRole
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import java.util.UUID

object Roles {
    const val SUPER_ADMIN = "SUPER_ADMIN"
    const val BUSINESS_OWNER = "BUSINESS_OWNER"
    const val BUSINESS_STAFF = "BUSINESS_STAFF"

    fun forBusinessRole(role: BusinessRole) = when (role) {
        BusinessRole.OWNER -> BUSINESS_OWNER
        BusinessRole.STAFF -> BUSINESS_STAFF
    }
}

/** Principal de la petición, construido en el servidor a partir de la sesión y `business_members`. */
data class AuthenticatedUser(
    val userId: UUID,
    val sessionId: UUID,
    val email: String,
    val roles: Set<String>,
    val businessId: UUID?,
    val businessRole: BusinessRole?,
)

class SessionAuthentication(val user: AuthenticatedUser) :
    AbstractAuthenticationToken(user.roles.map { SimpleGrantedAuthority("ROLE_$it") as GrantedAuthority }) {
    init {
        isAuthenticated = true
    }

    override fun getCredentials(): Any? = null

    override fun getPrincipal(): AuthenticatedUser = user
}
