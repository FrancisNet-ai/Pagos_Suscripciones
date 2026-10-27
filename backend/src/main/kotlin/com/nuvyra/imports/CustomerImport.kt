package com.nuvyra.imports

import com.nuvyra.common.persistence.VersionedEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.LockModeType
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class ImportStatus { PREVIEW, COMMITTED, DISCARDED }

/** CREATE: cliente nuevo. EXISTING: ya existe (se conserva; solo se agrega la suscripción). ERROR: no se importa. */
enum class RowAction { CREATE, EXISTING, ERROR }

enum class RowOutcome { CREATED, SUBSCRIPTION_ADDED, UNCHANGED, FAILED, SKIPPED }

data class ImportRow(
    val rowNumber: Int,
    val fullName: String?,
    val phone: String?,
    val notes: String?,
    val productId: UUID?,
    val productName: String?,
    val startDate: LocalDate?,
    val durationDays: Int?,
    val price: BigDecimal?,
    val status: com.nuvyra.subscriptions.ManualStatus?,
    val action: RowAction,
    val errors: List<String>,
    val outcome: RowOutcome? = null,
    val outcomeMessage: String? = null,
) {
    val hasSubscription: Boolean get() = productId != null
}

@Entity
@Table(name = "customer_imports")
class CustomerImport(
    @Column(nullable = false, updatable = false) val businessId: UUID,
    @Column(nullable = false, updatable = false) val fileName: String,
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") var rows: String,
    @Column(nullable = false, updatable = false) val totalRows: Int,
    @Column(nullable = false, updatable = false) val validRows: Int,
    @Column(nullable = false, updatable = false) val createdBy: UUID,
    @Column(nullable = false, updatable = false) val expiresAt: Instant,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var status: ImportStatus = ImportStatus.PREVIEW,
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") var result: String? = null,
    var committedAt: Instant? = null,
) : VersionedEntity()

interface CustomerImportRepository : JpaRepository<CustomerImport, UUID> {
    fun findByIdAndBusinessId(id: UUID, businessId: UUID): CustomerImport?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from CustomerImport i where i.id = :id")
    fun lockById(id: UUID): CustomerImport?
}

data class ImportSummary(val toCreate: Int, val existing: Int, val withSubscription: Int, val errors: Int)

data class ImportResultSummary(val created: Int, val subscriptionsAdded: Int, val unchanged: Int, val failed: Int, val skipped: Int)

data class ImportResponse(
    val id: UUID,
    val fileName: String,
    val status: ImportStatus,
    val totalRows: Int,
    val summary: ImportSummary,
    val result: ImportResultSummary?,
    val expiresAt: Instant,
    val rows: List<ImportRow>,
)
