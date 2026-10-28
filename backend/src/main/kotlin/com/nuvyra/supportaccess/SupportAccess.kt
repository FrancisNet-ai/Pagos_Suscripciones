package com.nuvyra.supportaccess

import com.nuvyra.businesses.BusinessService
import com.nuvyra.common.audit.AuditActions
import com.nuvyra.common.audit.AuditService
import com.nuvyra.common.error.ApiException
import com.nuvyra.common.web.PageResponse
import com.nuvyra.customers.CustomerAdminService
import com.nuvyra.customers.CustomerFilter
import com.nuvyra.customers.CustomerSummary
import com.nuvyra.orders.OrderFilter
import com.nuvyra.orders.OrderService
import com.nuvyra.orders.OrderSummary
import com.nuvyra.security.AuthenticatedUser
import com.nuvyra.security.CurrentBusiness
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

data class GrantSupportRequest(
    @field:NotNull(message = "Indica por cuántas horas") @field:Min(1, message = "Mínimo 1 hora") @field:Max(72, message = "Máximo 72 horas")
    val hours: Int?,
    @field:Size(max = 300, message = "La nota tiene como máximo 300 caracteres")
    val note: String? = null,
)

data class SupportGrant(val id: UUID, val grantedBy: String, val note: String?, val createdAt: Instant, val expiresAt: Instant, val revokedAt: Instant?, val active: Boolean)

data class SupportView(val resource: String, val reason: String, val at: Instant)

data class SupportAccessResponse(val active: SupportGrant?, val grants: List<SupportGrant>, val views: List<SupportView>)

/**
 * El superadministrador NO ve datos de compradores salvo que el titular lo autorice por un tiempo
 * limitado. Cada consulta exige un motivo y queda auditada en el negocio, a la vista del titular.
 */
@Service
class SupportAccessService(
    private val jdbc: JdbcTemplate,
    private val audit: AuditService,
    private val orders: OrderService,
    private val customers: CustomerAdminService,
    private val businesses: BusinessService,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun status(businessId: UUID): SupportAccessResponse {
        val grants = jdbc.query(
            """
            select g.id, u.full_name, g.note, g.created_at, g.expires_at, g.revoked_at from support_grants g join users u on u.id = g.granted_by
            where g.business_id = ? order by g.created_at desc limit 20
            """.trimIndent(),
            { rs, _ ->
                val expires = rs.getTimestamp(5).toInstant()
                val revoked = rs.getTimestamp(6)?.toInstant()
                SupportGrant(rs.getObject(1, UUID::class.java), rs.getString(2), rs.getString(3), rs.getTimestamp(4).toInstant(), expires, revoked, revoked == null && clock.instant().isBefore(expires))
            },
            businessId,
        )
        val views = jdbc.query(
            """
            select metadata ->> 'resource', metadata ->> 'reason', created_at from audit_logs
            where business_id = ? and action = ? order by created_at desc limit 50
            """.trimIndent(),
            { rs, _ -> SupportView(rs.getString(1), rs.getString(2), rs.getTimestamp(3).toInstant()) },
            businessId, AuditActions.SUPPORT_DATA_VIEWED,
        )
        return SupportAccessResponse(grants.firstOrNull { it.active }, grants, views)
    }

    @Transactional
    fun grant(businessId: UUID, actorId: UUID, request: GrantSupportRequest): SupportAccessResponse {
        revokeActive(businessId, actorId)
        val now = clock.instant()
        jdbc.update(
            "insert into support_grants (business_id, granted_by, note, expires_at, created_at) values (?, ?, ?, ?, ?)",
            businessId, actorId, request.note?.trim()?.ifBlank { null }, Timestamp.from(now.plus(Duration.ofHours(request.hours!!.toLong()))), Timestamp.from(now),
        )
        audit.record(AuditActions.SUPPORT_ACCESS_GRANTED, actorId = actorId, businessId = businessId, metadata = mapOf("hours" to request.hours))
        return status(businessId)
    }

    @Transactional
    fun revoke(businessId: UUID, actorId: UUID): SupportAccessResponse {
        if (revokeActive(businessId, actorId) > 0) audit.record(AuditActions.SUPPORT_ACCESS_REVOKED, actorId = actorId, businessId = businessId)
        return status(businessId)
    }

    /** Vencimiento de la autorización vigente, o `null` si no hay. */
    @Transactional(readOnly = true)
    fun activeUntil(businessId: UUID): Instant? = jdbc.query(
        "select max(expires_at) from support_grants where business_id = ? and revoked_at is null and expires_at > ?",
        { rs, _ -> rs.getTimestamp(1)?.toInstant() }, businessId, Timestamp.from(clock.instant()),
    ).firstOrNull()

    @Transactional
    fun orders(admin: AuthenticatedUser, businessId: UUID, reason: String?, query: String?, page: Int): PageResponse<OrderSummary> {
        authorize(admin, businessId, reason, "Pedidos")
        val zone = ZoneId.of(businesses.get(businessId).timeZone)
        return orders.list(businessId, zone, OrderFilter.ALL, null, null, null, query, page, 20)
    }

    @Transactional
    fun customers(admin: AuthenticatedUser, businessId: UUID, reason: String?, query: String?, page: Int): PageResponse<CustomerSummary> {
        authorize(admin, businessId, reason, "Clientes")
        return customers.list(businessId, query, CustomerFilter.ALL, page, 20)
    }

    private fun authorize(admin: AuthenticatedUser, businessId: UUID, reason: String?, resource: String) {
        val text = reason?.trim().orEmpty()
        if (text.length !in 10..300) throw ApiException.invalidField("reason", "Explica el motivo de la consulta (entre 10 y 300 caracteres)")
        activeUntil(businessId) ?: throw ApiException(
            HttpStatus.FORBIDDEN, "SUPPORT_ACCESS_REQUIRED", "El titular no autorizó el acceso de soporte o la autorización venció.",
        )
        audit.record(
            AuditActions.SUPPORT_DATA_VIEWED, actorId = admin.userId, businessId = businessId,
            metadata = mapOf("resource" to resource, "reason" to text),
        )
    }

    private fun revokeActive(businessId: UUID, actorId: UUID): Int = jdbc.update(
        "update support_grants set revoked_at = ?, revoked_by = ? where business_id = ? and revoked_at is null and expires_at > ?",
        Timestamp.from(clock.instant()), actorId, businessId, Timestamp.from(clock.instant()),
    )
}

/** Titular: autoriza o retira el acceso de soporte y ve cada consulta realizada. */
@RestController
@RequestMapping("/api/v1/business/me/support-access")
@Tag(name = "Acceso de soporte")
class SupportAccessController(private val service: SupportAccessService) {
    @GetMapping
    fun status(current: CurrentBusiness) = service.status(current.businessId)

    @PostMapping
    @Operation(summary = "Autoriza a soporte a consultar pedidos y clientes por un tiempo limitado")
    fun grant(current: CurrentBusiness, @Valid @RequestBody body: GrantSupportRequest) = service.grant(current.businessId, current.userId, body)

    @DeleteMapping
    fun revoke(current: CurrentBusiness) = service.revoke(current.businessId, current.userId)
}

/** Superadministración: solo lectura, con motivo obligatorio y autorización vigente del titular. */
@RestController
@RequestMapping("/api/v1/admin/businesses/{businessId}/support")
@Tag(name = "Acceso de soporte")
class AdminSupportController(private val service: SupportAccessService) {
    @GetMapping("/orders")
    fun orders(
        admin: AuthenticatedUser,
        @PathVariable businessId: UUID,
        @RequestParam(required = false) reason: String?,
        @RequestParam(required = false) query: String?,
        @RequestParam(defaultValue = "0") page: Int,
    ) = service.orders(admin, businessId, reason, query, page)

    @GetMapping("/customers")
    fun customers(
        admin: AuthenticatedUser,
        @PathVariable businessId: UUID,
        @RequestParam(required = false) reason: String?,
        @RequestParam(required = false) query: String?,
        @RequestParam(defaultValue = "0") page: Int,
    ) = service.customers(admin, businessId, reason, query, page)
}
