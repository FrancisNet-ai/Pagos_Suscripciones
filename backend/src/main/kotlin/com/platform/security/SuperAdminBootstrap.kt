package com.platform.security

import com.platform.common.audit.AuditActions
import com.platform.common.audit.AuditService
import com.platform.common.config.AppProperties
import com.platform.common.validation.PeruPhone
import com.platform.users.PlatformRole
import com.platform.users.User
import com.platform.users.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** Crea el superadministrador inicial si se definieron sus variables de entorno y aún no existe. */
@Component
class SuperAdminBootstrap(
    private val properties: AppProperties,
    private val users: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val audit: AuditService,
) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun run(args: ApplicationArguments) {
        val config = properties.bootstrap
        val email = config.superAdminEmail?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return
        val password = config.superAdminPassword?.takeIf { it.isNotEmpty() } ?: return
        if (users.existsByEmail(email)) return
        require(password.length >= 12) { "APP_BOOTSTRAP_SUPER_ADMIN_PASSWORD debe tener al menos 12 caracteres" }

        val user = users.save(
            User(
                fullName = config.superAdminName,
                email = email,
                passwordHash = passwordEncoder.encode(password)!!,
                phone = PeruPhone.normalize(config.superAdminPhone) ?: config.superAdminPhone,
                platformRole = PlatformRole.SUPER_ADMIN,
            ),
        )
        audit.record(AuditActions.SUPER_ADMIN_BOOTSTRAPPED, actorId = user.id, entityType = "USER", entityId = user.id)
        log.info("Superadministrador inicial creado")
    }
}
