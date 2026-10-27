package com.platform.businesses

import com.platform.common.persistence.VersionedEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

enum class BusinessRole { OWNER, STAFF }

enum class MemberStatus { ACTIVE, REVOKED }

@Entity
@Table(name = "business_members")
class BusinessMember(
    @Column(nullable = false, updatable = false) val businessId: UUID,
    @Column(nullable = false, updatable = false) val userId: UUID,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var role: BusinessRole,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var status: MemberStatus = MemberStatus.ACTIVE,
) : VersionedEntity()

interface BusinessMemberRepository : JpaRepository<BusinessMember, UUID> {
    /** MVP: un usuario pertenece a un único negocio activo. Se toma el más antiguo de forma determinista. */
    fun findFirstByUserIdAndStatusOrderByCreatedAtAsc(userId: UUID, status: MemberStatus): BusinessMember?
}
