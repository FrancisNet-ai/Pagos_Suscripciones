package com.nuvyra.common.audit

import org.springframework.stereotype.Service
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.util.UUID

object AuditActions {
    const val USER_REGISTERED = "USER_REGISTERED"
    const val LOGIN_SUCCEEDED = "LOGIN_SUCCEEDED"
    const val LOGIN_FAILED = "LOGIN_FAILED"
    const val LOGOUT = "LOGOUT"
    const val PASSWORD_RESET_REQUESTED = "PASSWORD_RESET_REQUESTED"
    const val PASSWORD_RESET_COMPLETED = "PASSWORD_RESET_COMPLETED"
    const val PASSWORD_CHANGED = "PASSWORD_CHANGED"
    const val SESSION_REVOKED = "SESSION_REVOKED"
    const val TWO_FACTOR_ENABLED = "TWO_FACTOR_ENABLED"
    const val TWO_FACTOR_DISABLED = "TWO_FACTOR_DISABLED"
    const val TWO_FACTOR_RECOVERY_CODES_REGENERATED = "TWO_FACTOR_RECOVERY_CODES_REGENERATED"
    const val TWO_FACTOR_RECOVERY_CODE_USED = "TWO_FACTOR_RECOVERY_CODE_USED"
    const val LOGIN_SECOND_FACTOR_FAILED = "LOGIN_SECOND_FACTOR_FAILED"
    const val SUPER_ADMIN_BOOTSTRAPPED = "SUPER_ADMIN_BOOTSTRAPPED"
    const val BUSINESS_UPDATED = "BUSINESS_UPDATED"
    const val BUSINESS_SLUG_CHANGED = "BUSINESS_SLUG_CHANGED"
    const val BUSINESS_IMAGE_CHANGED = "BUSINESS_IMAGE_CHANGED"
    const val PAYMENT_METHOD_UPDATED = "PAYMENT_METHOD_UPDATED"
    const val PRODUCT_CREATED = "PRODUCT_CREATED"
    const val PRODUCT_UPDATED = "PRODUCT_UPDATED"
    const val PRODUCT_STATUS_CHANGED = "PRODUCT_STATUS_CHANGED"
    const val ORDER_CREATED = "ORDER_CREATED"
    const val ORDER_CANCELLED = "ORDER_CANCELLED"
    const val ORDER_EXPIRED = "ORDER_EXPIRED"
    const val PAYMENT_REPORTED = "PAYMENT_REPORTED"
    const val PAYMENT_VERIFIED = "PAYMENT_VERIFIED"
    const val PAYMENT_REJECTED = "PAYMENT_REJECTED"
    const val RECEIPT_VIEWED = "RECEIPT_VIEWED"
    const val CUSTOMER_CREATED = "CUSTOMER_CREATED"
    const val CUSTOMER_UPDATED = "CUSTOMER_UPDATED"
    const val SUBSCRIPTION_CREATED = "SUBSCRIPTION_CREATED"
    const val SUBSCRIPTION_REGISTERED = "SUBSCRIPTION_REGISTERED"
    const val SUBSCRIPTION_ACTIVATED = "SUBSCRIPTION_ACTIVATED"
    const val SUBSCRIPTION_CANCELLED = "SUBSCRIPTION_CANCELLED"
    const val SUBSCRIPTION_EXPIRED = "SUBSCRIPTION_EXPIRED"
    const val RENEWAL_REFUNDED = "RENEWAL_REFUNDED"
    const val CAPACITY_RELEASED = "CAPACITY_RELEASED"
    const val RENEWAL_ACCEPTED = "RENEWAL_ACCEPTED"
    const val RENEWAL_DECLINED = "RENEWAL_DECLINED"
    const val RENEWAL_COMPLETED = "RENEWAL_COMPLETED"
    const val RENEWAL_EXPIRED = "RENEWAL_EXPIRED"
    const val RENEWAL_ORDER_CREATED = "RENEWAL_ORDER_CREATED"
    const val NOTIFICATION_SENT = "NOTIFICATION_SENT"
    const val NOTIFICATION_SKIPPED = "NOTIFICATION_SKIPPED"
    const val PAYMENT_REFUNDED = "PAYMENT_REFUNDED"
    const val NOTIFICATIONS_OPT_OUT = "NOTIFICATIONS_OPT_OUT"
    const val RENEWAL_SETTINGS_UPDATED = "RENEWAL_SETTINGS_UPDATED"
    const val SAAS_PLAN_CREATED = "SAAS_PLAN_CREATED"
    const val SAAS_PLAN_UPDATED = "SAAS_PLAN_UPDATED"
    const val SAAS_CHECKOUT_STARTED = "SAAS_CHECKOUT_STARTED"
    const val SAAS_INVOICE_CANCELLED = "SAAS_INVOICE_CANCELLED"
    const val SAAS_PAYMENT_REPORTED = "SAAS_PAYMENT_REPORTED"
    const val SAAS_PAYMENT_VERIFIED = "SAAS_PAYMENT_VERIFIED"
    const val SAAS_PAYMENT_REJECTED = "SAAS_PAYMENT_REJECTED"
    const val SAAS_RECEIPT_VIEWED = "SAAS_RECEIPT_VIEWED"
    const val SAAS_SUBSCRIPTION_ACTIVATED = "SAAS_SUBSCRIPTION_ACTIVATED"
    const val SAAS_SUBSCRIPTION_PAST_DUE = "SAAS_SUBSCRIPTION_PAST_DUE"
    const val SAAS_SUBSCRIPTION_EXPIRED = "SAAS_SUBSCRIPTION_EXPIRED"
    const val BUSINESS_SUSPENDED = "BUSINESS_SUSPENDED"
    const val BUSINESS_REACTIVATED = "BUSINESS_REACTIVATED"
    const val PLATFORM_SETTINGS_UPDATED = "PLATFORM_SETTINGS_UPDATED"
    const val WHATSAPP_INTEGRATION_UPDATED = "WHATSAPP_INTEGRATION_UPDATED"
    const val WHATSAPP_INTEGRATION_REMOVED = "WHATSAPP_INTEGRATION_REMOVED"
    const val CUSTOMERS_IMPORTED = "CUSTOMERS_IMPORTED"
    const val STAFF_INVITED = "STAFF_INVITED"
    const val STAFF_INVITATION_CANCELLED = "STAFF_INVITATION_CANCELLED"
    const val STAFF_JOINED = "STAFF_JOINED"
    const val STAFF_PERMISSIONS_UPDATED = "STAFF_PERMISSIONS_UPDATED"
    const val STAFF_REVOKED = "STAFF_REVOKED"
    const val STAFF_REACTIVATED = "STAFF_REACTIVATED"
    const val TASK_ASSIGNED = "TASK_ASSIGNED"
    const val BUSINESS_CREATED = "BUSINESS_CREATED"
    const val SUPPORT_ACCESS_GRANTED = "SUPPORT_ACCESS_GRANTED"
    const val SUPPORT_ACCESS_REVOKED = "SUPPORT_ACCESS_REVOKED"
    const val SUPPORT_DATA_VIEWED = "SUPPORT_DATA_VIEWED"
}

/** Evidencia inmutable de una acción; los módulos interesados reaccionan dentro de la transacción. */
data class AuditRecordedEvent(val entry: AuditLog)

@Service
class AuditService(
    private val repository: AuditLogRepository,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
    private val events: ApplicationEventPublisher,
) {
    /** Se une a la transacción actual: si la operación se revierte, su auditoría también. */
    @Transactional(propagation = Propagation.REQUIRED)
    fun record(
        action: String,
        actorId: UUID? = null,
        businessId: UUID? = null,
        entityType: String? = null,
        entityId: UUID? = null,
        metadata: Map<String, Any?> = emptyMap(),
        ipAddress: String? = null,
    ) {
        val entry = repository.save(
            AuditLog(
                actorId = actorId,
                businessId = businessId,
                action = action,
                entityType = entityType,
                entityId = entityId,
                metadata = objectMapper.writeValueAsString(metadata),
                ipAddress = ipAddress,
                createdAt = clock.instant(),
            ),
        )
        events.publishEvent(AuditRecordedEvent(entry))
    }
}
