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
