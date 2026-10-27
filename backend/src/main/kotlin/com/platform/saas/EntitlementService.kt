package com.platform.saas

import com.platform.businesses.Business
import com.platform.businesses.BusinessRepository
import com.platform.businesses.BusinessStatus
import com.platform.common.error.ApiException
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Por qué un negocio no puede usar las funciones comerciales. */
enum class AccessBlock(val message: String) {
    SUSPENDED("Tu negocio está suspendido. Escribe a soporte para revisar tu caso."),
    NO_PLAN("Elige un plan para empezar a vender."),
    PENDING_PAYMENT("Tu plan se activará cuando verifiquemos tu pago."),
    EXPIRED("Tu plan venció. Renuévalo para seguir vendiendo; tus datos se conservan."),
    CANCELLED("No tienes un plan vigente. Elige uno para seguir vendiendo."),
}

data class BusinessAccess(
    val active: Boolean,
    val block: AccessBlock?,
    val subscriptionStatus: SaasSubscriptionStatus?,
    val plan: SaasPlan?,
    val expiresAt: Instant?,
    /** Fecha límite real de uso: vencimiento + plazo de gracia. */
    val usableUntil: Instant?,
    val suspensionReason: String?,
) {
    fun has(feature: PlanFeature) = active && plan?.has(feature) == true
}

/**
 * Derechos de uso de un negocio según su plan SaaS. Es la única fuente de verdad para saber si un
 * vendedor puede vender, qué funciones incluye su plan y si alcanzó sus límites.
 */
@Service
class EntitlementService(
    private val businesses: BusinessRepository,
    private val subscriptions: SaasSubscriptionRepository,
    private val plans: SaasPlanRepository,
    private val settings: PlatformSettingsRepository,
    private val jdbc: JdbcTemplate,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun access(businessId: UUID): BusinessAccess = access(businesses.findById(businessId).orElseThrow { ApiException.notFound() })

    @Transactional(readOnly = true)
    fun access(business: Business): BusinessAccess {
        val sub = subscriptions.findByBusinessId(business.id)
        val plan = sub?.let { plans.findById(it.planId).orElse(null) }
        val usableUntil = sub?.let(::usableUntil)
        val now = clock.instant()
        val block = when {
            business.status == BusinessStatus.SUSPENDED -> AccessBlock.SUSPENDED
            sub == null -> AccessBlock.NO_PLAN
            sub.status == SaasSubscriptionStatus.PENDING_PAYMENT -> AccessBlock.PENDING_PAYMENT
            sub.status == SaasSubscriptionStatus.CANCELLED -> AccessBlock.CANCELLED
            usableUntil == null || !usableUntil.isAfter(now) -> AccessBlock.EXPIRED
            else -> null
        }
        return BusinessAccess(
            active = block == null,
            block = block,
            subscriptionStatus = sub?.status,
            plan = plan,
            expiresAt = sub?.expiresAt,
            usableUntil = usableUntil,
            suspensionReason = business.suspensionReason.takeIf { business.status == BusinessStatus.SUSPENDED },
        )
    }

    /** ACTIVE vale hasta el vencimiento + gracia aunque la tarea programada aún no lo haya marcado PAST_DUE. */
    private fun usableUntil(sub: SaasSubscription): Instant? = when (sub.status) {
        SaasSubscriptionStatus.ACTIVE -> sub.expiresAt?.plus(Duration.ofDays(graceDays().toLong()))
        SaasSubscriptionStatus.PAST_DUE -> sub.graceUntil
        else -> null
    }

    fun graceDays(): Int = settings.findById(1).map { it.saasGraceDays }.orElse(0)

    fun canSell(business: Business): Boolean = access(business).active

    @Transactional(readOnly = true)
    fun requireActive(businessId: UUID) {
        val access = access(businessId)
        access.block?.let { throw blocked(it) }
    }

    @Transactional(readOnly = true)
    fun requireFeature(businessId: UUID, feature: PlanFeature) {
        val access = access(businessId)
        access.block?.let { throw blocked(it) }
        if (!access.has(feature)) {
            throw ApiException(
                HttpStatus.FORBIDDEN, "FEATURE_NOT_IN_PLAN",
                "Tu plan ${access.plan?.name} no incluye ${feature.label.lowercase()}. Puedes cambiar de plan en «Mi plan».",
            )
        }
    }

    fun hasFeature(businessId: UUID, feature: PlanFeature): Boolean = access(businessId).has(feature)

    @Transactional(readOnly = true)
    fun usage(businessId: UUID) = Usage(
        products = jdbc.queryForObject(
            "select count(*) from products where business_id = ? and publication_status <> 'ARCHIVED'", Long::class.java, businessId,
        )!!,
        customers = jdbc.queryForObject("select count(*) from customers where business_id = ?", Long::class.java, businessId)!!,
    )

    /** Antes de crear o reactivar un producto. Los productos archivados no cuentan. */
    @Transactional(propagation = Propagation.MANDATORY)
    fun ensureProductCapacity(businessId: UUID) {
        val limit = access(businessId).plan?.productLimit ?: return
        lockLimits(businessId)
        if (usage(businessId).products >= limit) {
            throw ApiException.conflict(
                "PLAN_LIMIT_REACHED",
                "Tu plan permite $limit productos activos. Archiva alguno o cambia de plan para agregar más.",
            )
        }
    }

    /**
     * Antes de registrar un cliente nuevo. En la tienda pública no se revela el plan del vendedor:
     * el comprador solo ve que la tienda no recibe pedidos nuevos.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    fun ensureCustomerCapacity(businessId: UUID, phone: String, storefront: Boolean) {
        val limit = access(businessId).plan?.customerLimit ?: return
        val exists = jdbc.queryForObject(
            "select exists(select 1 from customers where business_id = ? and phone = ?)", Boolean::class.java, businessId, phone,
        )!!
        if (exists) return
        lockLimits(businessId)
        if (usage(businessId).customers >= limit) {
            throw if (storefront) {
                ApiException.conflict("STORE_NOT_ACCEPTING_ORDERS", "Esta tienda no está recibiendo pedidos nuevos por el momento.")
            } else {
                ApiException.conflict("PLAN_LIMIT_REACHED", "Tu plan permite $limit clientes. Cambia de plan para registrar más.")
            }
        }
    }

    /** Serializa altas concurrentes del mismo negocio para que el límite no se supere por una carrera. */
    private fun lockLimits(businessId: UUID) {
        jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?, 0))", "limits:$businessId")
    }

    companion object {
        fun blocked(block: AccessBlock): ApiException = ApiException(
            HttpStatus.FORBIDDEN,
            if (block == AccessBlock.SUSPENDED) "BUSINESS_SUSPENDED" else "PLAN_REQUIRED",
            block.message,
        )

        /**
         * Condición SQL: el negocio con alias `b` no está suspendido y tiene un plan vigente (incluida la
         * gracia). `planCondition` puede exigir funciones del plan (alias `sp`). Usa un parámetro `?` con el instante actual.
         */
        fun entitledSql(planCondition: String = "true") = """
            b.status = 'ACTIVE' and exists (
              select 1 from saas_subscriptions ss join saas_plans sp on sp.id = ss.plan_id
              where ss.business_id = b.id and ss.status in ('ACTIVE', 'PAST_DUE') and $planCondition
                and coalesce(ss.grace_until, ss.expires_at + make_interval(days => (select saas_grace_days from platform_settings where id = 1)))
                    > cast(? as timestamptz))
        """
    }
}

data class Usage(val products: Long, val customers: Long)
