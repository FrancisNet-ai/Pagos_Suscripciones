package com.nuvyra.businesses

import com.nuvyra.common.persistence.VersionedEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

enum class BusinessRole { OWNER, STAFF }

enum class MemberStatus { ACTIVE, REVOKED }

/**
 * Secciones que el titular puede habilitar a un colaborador. La configuración del negocio, los cobros
 * (Yape/Plin), WhatsApp Business, el plan SaaS y el equipo son siempre exclusivos del titular.
 */
enum class StaffPermission(val label: String) {
    PRODUCTS("Catálogo de productos"),
    ORDERS("Pedidos y atención de compradores"),
    PAYMENTS("Aprobación y rechazo de pagos"),
    CUSTOMERS("Clientes y suscripciones"),
    RENEWALS("Renovaciones y avisos"),
    REPORTS("Ingresos y reportes"),
    ;

    companion object {
        fun parse(raw: String): Set<StaffPermission> =
            raw.split(',').mapNotNull { p -> entries.firstOrNull { it.name == p.trim() } }.toSortedSet()

        fun format(permissions: Collection<StaffPermission>): String = permissions.toSortedSet().joinToString(",") { it.name }
    }
}

@Entity
@Table(name = "business_members")
class BusinessMember(
    @Column(nullable = false, updatable = false) val businessId: UUID,
    @Column(nullable = false, updatable = false) val userId: UUID,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var role: BusinessRole,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var status: MemberStatus = MemberStatus.ACTIVE,
    @Column(name = "permissions", nullable = false) var permissionsRaw: String = "",
    var revokedAt: Instant? = null,
) : VersionedEntity() {
    /** El titular tiene todas las secciones; un colaborador, solo las habilitadas. */
    var permissions: Set<StaffPermission>
        get() = if (role == BusinessRole.OWNER) StaffPermission.entries.toSet() else StaffPermission.parse(permissionsRaw)
        set(value) {
            permissionsRaw = StaffPermission.format(value)
        }
}

interface BusinessMemberRepository : JpaRepository<BusinessMember, UUID> {
    /** MVP: un usuario pertenece a un único negocio activo. Se toma el más antiguo de forma determinista. */
    fun findFirstByUserIdAndStatusOrderByCreatedAtAsc(userId: UUID, status: MemberStatus): BusinessMember?
    fun findByBusinessIdOrderByCreatedAtAsc(businessId: UUID): List<BusinessMember>
    fun findByIdAndBusinessId(id: UUID, businessId: UUID): BusinessMember?
    fun countByBusinessIdAndStatus(businessId: UUID, status: MemberStatus): Long
}
