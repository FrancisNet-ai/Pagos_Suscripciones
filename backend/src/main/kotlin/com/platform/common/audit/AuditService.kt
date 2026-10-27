package com.platform.common.audit

import org.springframework.stereotype.Service
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
}

@Service
class AuditService(
    private val repository: AuditLogRepository,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
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
        repository.save(
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
    }
}
