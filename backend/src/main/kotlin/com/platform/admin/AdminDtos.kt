package com.platform.admin

import com.platform.businesses.BusinessStatus
import com.platform.payments.PaymentMethodType
import com.platform.saas.AccessSummary
import com.platform.saas.InvoiceResponse
import com.platform.saas.SaasPaymentStatus
import com.platform.saas.SaasSubscriptionStatus
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** Filtros de negocios en el panel de superadministración. */
enum class BusinessFilter { ALL, ACTIVE, NO_PLAN, PENDING_PAYMENT, PAST_DUE, EXPIRED, SUSPENDED }

data class PlanCount(val planId: UUID, val planName: String, val businesses: Long)

data class MonthAmount(val month: String, val label: String, val amount: BigDecimal)

/** Solo dinero de la plataforma (saas_payments VERIFIED); nunca ventas de los vendedores. */
data class AdminOverview(
    val businessesTotal: Long,
    val businessesActive: Long,
    val businessesNoPlan: Long,
    val subscriptionsPendingPayment: Long,
    val subscriptionsPastDue: Long,
    val subscriptionsExpired: Long,
    val businessesSuspended: Long,
    val paymentsToVerify: Long,
    val revenueMonth: BigDecimal,
    val revenueTotal: BigDecimal,
    val newBusinesses30Days: Long,
    val plans: List<PlanCount>,
    val last12Months: List<MonthAmount>,
)

data class AdminBusinessSummary(
    val id: UUID,
    val businessName: String,
    val slug: String,
    val uniqueCode: String,
    val ownerName: String,
    val ownerEmail: String,
    val status: BusinessStatus,
    val planName: String?,
    val subscriptionStatus: SaasSubscriptionStatus?,
    val expiresAt: Instant?,
    val createdAt: Instant,
)

/** Solo agregados: el SUPER_ADMIN no ve compradores ni ventas individuales del vendedor. */
data class BusinessUsage(val products: Long, val customers: Long, val ordersPaid: Long)

data class AdminAuditEntry(
    val id: UUID,
    val action: String,
    val actorEmail: String?,
    val businessId: UUID?,
    val businessName: String?,
    val entityType: String?,
    /** Solo para acciones de la plataforma; las del vendedor pueden contener datos de compradores. */
    val metadata: String?,
    val ipAddress: String?,
    val createdAt: Instant,
)

data class AdminBusinessDetail(
    val summary: AdminBusinessSummary,
    val ownerPhone: String,
    val suspensionReason: String?,
    val suspendedAt: Instant?,
    val access: AccessSummary,
    val usage: BusinessUsage,
    val invoices: List<InvoiceResponse>,
    val activity: List<AdminAuditEntry>,
)

data class AdminSaasPayment(
    val id: UUID,
    val businessId: UUID,
    val businessName: String,
    val invoiceId: UUID,
    val invoiceNumber: String,
    val planName: String,
    val method: PaymentMethodType,
    val receiverHolder: String,
    val receiverPhone: String,
    val amount: BigDecimal,
    val transactionReference: String,
    val hasReceipt: Boolean,
    val status: SaasPaymentStatus,
    val reportedAt: Instant,
    val verifiedAt: Instant?,
    val rejectionReason: String?,
)
