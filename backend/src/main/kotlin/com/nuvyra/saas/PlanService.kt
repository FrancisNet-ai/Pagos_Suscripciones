package com.nuvyra.saas

import com.nuvyra.common.audit.AuditActions
import com.nuvyra.common.audit.AuditService
import com.nuvyra.common.error.ApiException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** Planes comerciales administrados por el SUPER_ADMIN. Nada de precios ni límites en el código. */
@Service
class PlanService(
    private val plans: SaasPlanRepository,
    private val jdbc: JdbcTemplate,
    private val audit: AuditService,
) {
    @Transactional(readOnly = true)
    fun publicPlans(): List<PlanResponse> = plans.findByActiveTrueOrderBySortOrderAscPriceAsc().map { PlanResponse.of(it) }

    @Transactional(readOnly = true)
    fun adminPlans(): List<PlanResponse> {
        val counts = jdbc.query(
            "select plan_id, count(*) from saas_subscriptions where status in ('ACTIVE', 'PAST_DUE') group by plan_id",
        ) { rs, _ -> rs.getObject(1, UUID::class.java) to rs.getLong(2) }.toMap()
        return plans.findAllByOrderBySortOrderAscPriceAsc().map { PlanResponse.of(it, counts[it.id] ?: 0) }
    }

    @Transactional
    fun create(actorId: UUID, request: PlanRequest): PlanResponse {
        val name = request.name!!.trim()
        if (plans.existsByNameIgnoreCase(name)) throw ApiException.invalidField("name", "Ya existe un plan con este nombre")
        val plan = plans.save(SaasPlan(name = name, price = request.price!!, billingPeriod = request.billingPeriod!!).also { it.applyRequest(request) })
        audit.record(AuditActions.SAAS_PLAN_CREATED, actorId = actorId, entityType = "SAAS_PLAN", entityId = plan.id, metadata = snapshot(plan))
        return PlanResponse.of(plan, 0)
    }

    /** Los cambios de precio no alteran cobros ya emitidos (guardan su propio importe). */
    @Transactional
    fun update(actorId: UUID, planId: UUID, request: PlanRequest): PlanResponse {
        val plan = plans.findById(planId).orElseThrow { ApiException.notFound("El plan no existe.") }
        val name = request.name!!.trim()
        if (plans.existsByNameIgnoreCaseAndIdNot(name, planId)) throw ApiException.invalidField("name", "Ya existe un plan con este nombre")
        plan.name = name
        plan.applyRequest(request)
        audit.record(AuditActions.SAAS_PLAN_UPDATED, actorId = actorId, entityType = "SAAS_PLAN", entityId = plan.id, metadata = snapshot(plan))
        return adminPlans().first { it.id == plan.id }
    }

    private fun SaasPlan.applyRequest(r: PlanRequest) {
        description = r.description?.trim()?.ifEmpty { null }
        price = r.price!!
        billingPeriod = r.billingPeriod!!
        productLimit = r.productLimit
        customerLimit = r.customerLimit
        adminUserLimit = r.adminUserLimit!!
        featureReports = r.featureReports!!
        featureReminders = r.featureReminders!!
        featureBranding = r.featureBranding!!
        active = r.active!!
        sortOrder = r.sortOrder!!
    }

    private fun snapshot(p: SaasPlan) = mapOf(
        "name" to p.name, "price" to p.price, "period" to p.billingPeriod.name, "productLimit" to p.productLimit,
        "customerLimit" to p.customerLimit, "features" to p.features.map { it.name }, "active" to p.active,
    )
}
