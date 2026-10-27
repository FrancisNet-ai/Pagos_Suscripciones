package com.nuvyra.common.audit

import com.nuvyra.common.persistence.AbstractEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.ColumnTransformer
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "audit_logs")
class AuditLog(
    @Column(updatable = false) val actorId: UUID?,
    @Column(updatable = false) val businessId: UUID?,
    @Column(nullable = false, updatable = false) val action: String,
    @Column(updatable = false) val entityType: String?,
    @Column(updatable = false) val entityId: UUID?,
    @ColumnTransformer(write = "?::jsonb")
    @Column(nullable = false, updatable = false, columnDefinition = "jsonb") val metadata: String,
    @Column(updatable = false) val ipAddress: String?,
    @Column(nullable = false, updatable = false) val createdAt: Instant,
) : AbstractEntity()

interface AuditLogRepository : JpaRepository<AuditLog, UUID> {
    fun findByBusinessIdOrderByCreatedAtDesc(businessId: UUID): List<AuditLog>
    fun findTop12ByBusinessIdOrderByCreatedAtDesc(businessId: UUID): List<AuditLog>
    fun findByActorIdOrderByCreatedAtDesc(actorId: UUID): List<AuditLog>
}
