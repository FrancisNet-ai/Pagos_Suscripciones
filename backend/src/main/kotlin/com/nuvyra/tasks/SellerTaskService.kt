package com.nuvyra.tasks

import com.nuvyra.businesses.BusinessRole
import com.nuvyra.businesses.StaffPermission
import com.nuvyra.common.audit.AuditActions
import com.nuvyra.common.audit.AuditRecordedEvent
import com.nuvyra.common.audit.AuditService
import com.nuvyra.common.error.ApiException
import com.nuvyra.common.web.PageResponse
import com.nuvyra.saas.EntitlementService
import com.nuvyra.saas.PlanFeature
import com.nuvyra.security.CurrentBusiness
import com.nuvyra.whatsapp.WhatsAppIntegrationRepository
import jakarta.persistence.EntityManager
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Clock
import java.util.UUID

/**
 * Pendientes del vendedor. [sync] los abre según el estado de los datos. La acción auditada los cierra
 * en su transacción; la sincronización detecta también cambios automáticos. Nunca se marcan como
 * hechos a mano. Conservan responsable, plazo y evidencia del cierre sin atribuciones inventadas.
 */
@Service
class SellerTaskService(
    private val jdbc: NamedParameterJdbcTemplate,
    private val whatsAppIntegrations: WhatsAppIntegrationRepository,
    private val entitlements: EntitlementService,
    private val audit: AuditService,
    private val clock: Clock,
    private val entityManager: EntityManager,
) {
    /**
     * Cierra la tarea en la misma transacción de la acción. Así un pago rechazado y reportado otra vez
     * no pierde su primera resolución aunque nadie abra Pendientes entre ambas operaciones.
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun resolveFromAudit(event: AuditRecordedEvent) {
        val entry = event.entry
        val businessId = entry.businessId ?: return
        val types = when (entry.action) {
            AuditActions.PAYMENT_VERIFIED, AuditActions.PAYMENT_REJECTED, AuditActions.ORDER_CANCELLED -> listOf(TaskType.VERIFY_PAYMENT, TaskType.REVIEW_INCIDENT)
            AuditActions.SUBSCRIPTION_ACTIVATED, AuditActions.SUBSCRIPTION_CANCELLED -> listOf(TaskType.ACTIVATE_SERVICE)
            AuditActions.CAPACITY_RELEASED -> listOf(TaskType.RELEASE_ACCESS)
            AuditActions.NOTIFICATION_SENT, AuditActions.NOTIFICATION_SKIPPED, AuditActions.NOTIFICATIONS_OPT_OUT -> listOf(TaskType.SEND_RENEWAL_NOTICE)
            else -> return
        }
        jdbc.query("select pg_advisory_xact_lock(hashtextextended(:k, 0))", MapSqlParameterSource("k", "tasks:$businessId")) { _, _ -> }
        // Auditoría y estado JPA deben estar visibles para el SQL; todo se revierte si falla el commit.
        entityManager.flush()
        for (type in types) {
            jdbc.update(
                """
                update seller_tasks t set status = 'RESOLVED', resolved_at = al.created_at,
                    resolved_by = al.actor_id, resolution_action = al.action, resolution_audit_id = al.id,
                    updated_at = al.created_at, version = t.version + 1
                from audit_logs al
                where t.business_id = :b and t.task_type = :type and t.status = 'OPEN'
                  and al.id = :audit and al.business_id = :b and ${resolutionEvidence(type)}
                """.trimIndent(),
                MapSqlParameterSource("b", businessId).addValue("type", type.name).addValue("audit", entry.id),
            )
        }
    }

    @Transactional
    fun list(current: CurrentBusiness, view: TaskView): SellerTaskList {
        sync(current.businessId)
        val types = TaskType.visibleTo(current.isOwner, current.permissions)
        if (types.isEmpty()) return SellerTaskList(emptyList(), TaskCounts(0, 0, 0, 0), emptyList())
        val params = params(current.businessId)
            .addValue("types", types.map { it.name })
        val all = jdbc.query(LIST_SQL, params) { rs, _ -> toItem(rs) }
        val counts = TaskCounts(
            open = all.size.toLong(),
            mine = all.count { it.assignee?.userId == current.userId }.toLong(),
            unassigned = all.count { it.assignee == null }.toLong(),
            overdue = all.count { it.overdue }.toLong(),
        )
        val items = when (view) {
            TaskView.ALL -> all
            TaskView.MINE -> all.filter { it.assignee?.userId == current.userId }
            TaskView.UNASSIGNED -> all.filter { it.assignee == null }
        }
        return SellerTaskList(items, counts, members(current.businessId))
    }

    @Transactional
    fun counts(current: CurrentBusiness): TaskCounts = list(current, TaskView.ALL).counts

    @Transactional
    fun history(current: CurrentBusiness, page: Int, size: Int): PageResponse<ResolvedSellerTask> {
        sync(current.businessId)
        val pageNumber = maxOf(page, 0)
        val pageSize = size.coerceIn(1, PageResponse.MAX_SIZE)
        val types = TaskType.visibleTo(current.isOwner, current.permissions)
        if (types.isEmpty()) return PageResponse(emptyList(), pageNumber, pageSize, 0, 0)
        val p = MapSqlParameterSource("b", current.businessId)
            .addValue("types", types.map { it.name })
            .addValue("limit", pageSize).addValue("offset", pageNumber.toLong() * pageSize)
        val where = "where t.business_id = :b and t.status = 'RESOLVED' and t.task_type in (:types)"
        val total = jdbc.queryForObject("select count(*) from seller_tasks t $where", p, Long::class.java)!!
        val items = jdbc.query(
            "$CONTEXT_SQL $where order by t.resolved_at desc, t.id desc limit :limit offset :offset", p,
        ) { rs, _ ->
            val action = TaskResolutionAction.valueOf(rs.getString("resolution_action") ?: "STATE_CHANGED")
            ResolvedSellerTask(
                task = toItem(rs), resolvedAt = rs.getTimestamp("resolved_at").toInstant(),
                resolvedBy = rs.getObject("resolved_by", UUID::class.java)?.let { TaskAssignee(it, rs.getString("resolver_name")) },
                resolutionAction = action, resolutionLabel = action.label,
            )
        }
        return PageResponse(items, pageNumber, pageSize, total, ((total + pageSize - 1) / pageSize).toInt())
    }

    /**
     * Asigna o libera una tarea. El titular asigna a cualquier miembro que pueda verla; un colaborador
     * solo puede tomarla para sí o soltar la que tiene.
     */
    @Transactional
    fun assign(current: CurrentBusiness, taskId: UUID, userId: UUID?): SellerTaskList {
        sync(current.businessId)
        val row = jdbc.query(
            "select task_type, assigned_to from seller_tasks where id = :id and business_id = :b and status = 'OPEN' for update",
            MapSqlParameterSource("id", taskId).addValue("b", current.businessId),
        ) { rs, _ -> TaskType.valueOf(rs.getString(1)) to rs.getObject(2, UUID::class.java) }.firstOrNull()
            ?: throw ApiException.notFound("La tarea ya se resolvió o no existe.")
        val (type, assignedTo) = row
        if (type !in TaskType.visibleTo(current.isOwner, current.permissions)) throw ApiException.notFound("La tarea ya se resolvió o no existe.")
        if (!current.isOwner) {
            val allowed = if (userId == null) assignedTo == null || assignedTo == current.userId else userId == current.userId
            if (!allowed) throw ApiException.forbidden("Solo puedes tomar tareas para ti o soltar las tuyas.")
        }
        if (userId != null && members(current.businessId).none { it.userId == userId && type in it.types }) {
            throw ApiException.invalidField("userId", "Esa persona no tiene acceso a «${type.label}».")
        }
        jdbc.update(
            """
            update seller_tasks set assigned_to = :u, assigned_at = :at, updated_at = :now, version = version + 1 where id = :id
            """.trimIndent(),
            MapSqlParameterSource("u", userId).addValue("id", taskId).addValue("now", Timestamp.from(clock.instant()))
                .addValue("at", userId?.let { Timestamp.from(clock.instant()) }, java.sql.Types.TIMESTAMP),
        )
        audit.record(
            AuditActions.TASK_ASSIGNED, actorId = current.userId, businessId = current.businessId, entityType = "SELLER_TASK", entityId = taskId,
            metadata = mapOf("assignedTo" to (userId?.toString() ?: "")),
        )
        return list(current, TaskView.ALL)
    }

    /** Alinea las tareas abiertas con el estado actual del negocio. Idempotente. */
    @Transactional
    fun sync(businessId: UUID) {
        val params = params(businessId)
        // Serializa sincronizaciones simultáneas del mismo negocio.
        jdbc.query("select pg_advisory_xact_lock(hashtextextended(:k, 0))", MapSqlParameterSource("k", "tasks:$businessId")) { _, _ -> }
        for ((type, source) in sources()) {
            val p = MapSqlParameterSource(params.values).addValue("type", type.name).addValue("slaHours", type.slaHours)
            jdbc.update(
                """
                with closing as (
                    select t.id, a.id as audit_id, a.actor_id, a.action, a.created_at
                    from seller_tasks t
                    left join lateral (
                        select al.id, al.actor_id, al.action, al.created_at from audit_logs al
                        where al.business_id = :b and ${resolutionEvidence(type)}
                          and al.created_at >= t.created_at and al.created_at <= :now
                        order by al.created_at, al.id limit 1
                    ) a on true
                    where t.business_id = :b and t.task_type = :type and t.status = 'OPEN'
                      and t.entity_id not in (select src.id from ($source) src)
                )
                update seller_tasks t set status = 'RESOLVED', resolved_at = coalesce(c.created_at, :now),
                    resolved_by = c.actor_id, resolution_action = coalesce(c.action, 'STATE_CHANGED'),
                    resolution_audit_id = c.audit_id, updated_at = :now, version = version + 1
                from closing c where t.id = c.id
                """.trimIndent(),
                p,
            )
            jdbc.update(
                """
                insert into seller_tasks (business_id, task_type, entity_id, since, due_at)
                select :b, :type, src.id, src.since, src.since + make_interval(hours => cast(:slaHours as int)) from ($source) src
                on conflict (business_id, task_type, entity_id) where status = 'OPEN' do nothing
                """.trimIndent(),
                p,
            )
        }
        // Quien perdió el acceso (revocado o sin el permiso) deja de ser responsable.
        for (type in TaskType.entries) {
            jdbc.update(
                """
                update seller_tasks t set assigned_to = null, assigned_at = null, updated_at = :now
                where t.business_id = :b and t.status = 'OPEN' and t.task_type = :type and t.assigned_to is not null
                  and not exists (select 1 from business_members m
                                  where m.business_id = t.business_id and m.user_id = t.assigned_to and m.status = 'ACTIVE'
                                    and (m.role = 'OWNER' or ${permissionMatch(type)}))
                """.trimIndent(),
                MapSqlParameterSource(params.values).addValue("type", type.name),
            )
        }
    }

    private fun members(businessId: UUID): List<AssignableMember> = jdbc.query(
        """
        select u.id, u.full_name, m.role, m.permissions from business_members m join users u on u.id = m.user_id
        where m.business_id = :b and m.status = 'ACTIVE' order by m.role desc, u.full_name
        """.trimIndent(),
        MapSqlParameterSource("b", businessId),
    ) { rs, _ ->
        val owner = rs.getString(3) == BusinessRole.OWNER.name
        AssignableMember(
            rs.getObject(1, UUID::class.java), rs.getString(2), owner,
            TaskType.visibleTo(owner, StaffPermission.parse(rs.getString(4))),
        )
    }

    private fun params(businessId: UUID): MapSqlParameterSource {
        val integration = whatsAppIntegrations.findById(businessId).orElse(null)
            ?.takeIf { it.active && entitlements.hasFeature(businessId, PlanFeature.REMINDERS) }
        return MapSqlParameterSource("b", businessId)
            .addValue("now", Timestamp.from(clock.instant()))
            .addValue("wa", integration != null)
            .addValue("farewell", integration?.farewellTemplate != null)
    }

    private fun toItem(rs: ResultSet): SellerTaskItem {
        val type = TaskType.valueOf(rs.getString("task_type"))
        val due = rs.getTimestamp("due_at").toInstant()
        val assigneeId = rs.getObject("assigned_to", UUID::class.java)
        return SellerTaskItem(
            id = rs.getObject("id", UUID::class.java),
            type = type,
            typeLabel = type.label,
            since = rs.getTimestamp("since").toInstant(),
            dueAt = due,
            overdue = !clock.instant().isBefore(due),
            assignee = assigneeId?.let { TaskAssignee(it, rs.getString("assignee_name")) },
            customerId = rs.getObject("customer_id", UUID::class.java),
            customerName = rs.getString("customer_name"),
            productName = rs.getString("product_name"),
            orderId = rs.getObject("order_id", UUID::class.java),
            orderNumber = rs.getString("order_number"),
            amount = rs.getBigDecimal("amount"),
            subscriptionId = rs.getObject("subscription_id", UUID::class.java),
            detail = when (type) {
                TaskType.REVIEW_INCIDENT -> rs.getString("incident_reason")
                TaskType.SEND_RENEWAL_NOTICE -> when (rs.getString("notification_type")) {
                    "REMINDER_BEFORE" -> "Recordatorio previo"
                    "REMINDER_DUE_DAY" -> "Vence hoy"
                    "FAREWELL" -> "Despedida"
                    else -> null
                }.let { if (rs.getString("delivery_status") == "FAILED") "$it · el envío automático falló" else it }
                else -> null
            },
        )
    }

    private companion object {
        /** Solo la acción auditada de esta necesidad acredita su autor; nunca el asignado o el lector. */
        fun resolutionEvidence(type: TaskType): String = when (type) {
            TaskType.VERIFY_PAYMENT, TaskType.REVIEW_INCIDENT ->
                """
                    ((al.entity_type = 'PAYMENT' and al.entity_id = t.entity_id and al.action in ('PAYMENT_VERIFIED', 'PAYMENT_REJECTED'))
                    or (al.entity_type = 'ORDER' and al.action = 'ORDER_CANCELLED'
                        and al.entity_id = (select order_id from customer_payments where id = t.entity_id and business_id = :b)))
                """.trimIndent()
            TaskType.ACTIVATE_SERVICE ->
                "al.entity_type = 'SUBSCRIPTION' and al.entity_id = t.entity_id and al.action in ('SUBSCRIPTION_ACTIVATED', 'SUBSCRIPTION_CANCELLED')"
            TaskType.RELEASE_ACCESS -> """
                al.entity_type = 'SUBSCRIPTION' and al.action = 'CAPACITY_RELEASED'
                and al.entity_id = (select customer_subscription_id from capacity_allocations where id = t.entity_id and business_id = :b)
            """.trimIndent()
            TaskType.SEND_RENEWAL_NOTICE ->
                """
                    ((al.entity_type = 'NOTIFICATION' and al.entity_id = t.entity_id and al.action in ('NOTIFICATION_SENT', 'NOTIFICATION_SKIPPED'))
                    or (al.entity_type = 'CUSTOMER' and al.action = 'NOTIFICATIONS_OPT_OUT' and al.metadata ->> 'optedOut' = 'true'
                        and al.entity_id = (select customer_id from renewal_notifications where id = t.entity_id and business_id = :b)))
                """.trimIndent()
        }

        /** Condición SQL: el miembro `m` tiene alguno de los permisos que exige el tipo. */
        fun permissionMatch(type: TaskType) =
            type.anyOf.joinToString(" or ", "(", ")") { "(',' || m.permissions || ',') like '%,${it.name},%'" }

        /** Consultas de origen: (id, since) de cada necesidad vigente, por tipo. Parámetros :b, :now, :wa, :farewell. */
        fun sources() = listOf(
            TaskType.VERIFY_PAYMENT to """
                select p.id, coalesce(p.reported_at, p.created_at) as since
                from customer_payments p join orders o on o.id = p.order_id
                where p.business_id = :b and p.status = 'PENDING_VERIFICATION' and o.incident_reason is null
            """,
            TaskType.REVIEW_INCIDENT to """
                select p.id, coalesce(p.reported_at, p.created_at) as since
                from customer_payments p join orders o on o.id = p.order_id
                where p.business_id = :b and p.status = 'PENDING_VERIFICATION' and o.incident_reason is not null
            """,
            TaskType.ACTIVATE_SERVICE to """
                select s.id, coalesce(s.paid_at, s.created_at) as since
                from customer_subscriptions s where s.business_id = :b and s.status = 'PENDING_ACTIVATION'
            """,
            // Solo cuando el acceso ya terminó: antes, el cliente aún tiene derecho a usarlo.
            TaskType.RELEASE_ACCESS to """
                select a.id, coalesce(s.expires_at, a.updated_at) as since
                from capacity_allocations a left join customer_subscriptions s on s.id = a.customer_subscription_id
                where a.business_id = :b and a.allocation_status = 'RELEASE_PENDING' and (s.expires_at is null or s.expires_at <= :now)
            """,
            // Los avisos que enviará la integración oficial de WhatsApp no requieren al vendedor.
            TaskType.SEND_RENEWAL_NOTICE to """
                select n.id, n.scheduled_at as since
                from renewal_notifications n
                where n.business_id = :b and n.scheduled_at <= :now
                  and (n.delivery_status = 'FAILED' or (n.delivery_status = 'PENDING' and not (
                       cast(:wa as boolean)
                       and (n.notification_type <> 'FAREWELL' or cast(:farewell as boolean))
                       and coalesce((select c.granted and c.revoked_at is null from customer_consents c
                                     where c.customer_id = n.customer_id and c.purpose = 'RENEWAL_REMINDERS'
                                     order by c.created_at desc limit 1), false))))
            """,
        )

        val CONTEXT_SQL = """
            select t.id, t.task_type, t.since, t.due_at, t.assigned_to, au.full_name as assignee_name,
                   t.resolved_at, t.resolved_by, ru.full_name as resolver_name, t.resolution_action,
                   coalesce(o.customer_id, s.customer_id, sa.customer_id, n.customer_id) as customer_id,
                   coalesce(o.customer_name_snapshot, c.full_name) as customer_name,
                   coalesce(s.product_name_snapshot, sa.product_name_snapshot, pa.name, sn.product_name_snapshot) as product_name,
                   o.id as order_id, o.order_number, p.amount, o.incident_reason,
                   coalesce(s.id, sa.id, sn.id) as subscription_id,
                   n.notification_type, n.delivery_status
            from seller_tasks t
            left join users au on au.id = t.assigned_to
            left join users ru on ru.id = t.resolved_by
            left join customer_payments p on t.task_type in ('VERIFY_PAYMENT', 'REVIEW_INCIDENT') and p.id = t.entity_id
            left join orders o on o.id = p.order_id
            left join customer_subscriptions s on t.task_type = 'ACTIVATE_SERVICE' and s.id = t.entity_id
            left join capacity_allocations a on t.task_type = 'RELEASE_ACCESS' and a.id = t.entity_id
            left join customer_subscriptions sa on sa.id = a.customer_subscription_id
            left join products pa on pa.id = a.product_id
            left join renewal_notifications n on t.task_type = 'SEND_RENEWAL_NOTICE' and n.id = t.entity_id
            left join renewal_requests r on r.id = n.renewal_request_id
            left join customer_subscriptions sn on sn.id = r.subscription_id
            left join customers c on c.id = coalesce(s.customer_id, sa.customer_id, n.customer_id)
        """.trimIndent()

        val LIST_SQL = "$CONTEXT_SQL where t.business_id = :b and t.status = 'OPEN' and t.task_type in (:types) order by t.due_at, t.since"
    }
}
