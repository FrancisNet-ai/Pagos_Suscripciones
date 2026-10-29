package com.nuvyra.security

import com.nuvyra.common.audit.AuditActions
import com.nuvyra.common.audit.AuditService
import com.nuvyra.common.error.ApiException
import com.nuvyra.common.web.clientIp
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

data class SessionDto(
    val id: UUID,
    /** Navegador y sistema, deducidos del User-Agent (p. ej. «Chrome en Windows»). */
    val device: String,
    val ipAddress: String?,
    val createdAt: Instant,
    val lastUsedAt: Instant,
    val current: Boolean,
)

data class RevokedSessionsResponse(val closed: Int)

@RestController
@RequestMapping("/api/v1/auth/sessions")
@Tag(name = "Autenticación")
class SessionsController(private val sessions: SessionService, private val audit: AuditService) {

    @GetMapping
    @Operation(summary = "Sesiones abiertas de la cuenta, en este y otros dispositivos")
    fun list(user: AuthenticatedUser): List<SessionDto> = sessions.active(user.userId).map {
        SessionDto(it.id, DeviceLabel.of(it.userAgent), it.ipAddress, it.createdAt, it.lastUsedAt, it.id == user.sessionId)
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cierra una sesión en otro dispositivo")
    fun revoke(user: AuthenticatedUser, @PathVariable id: UUID, request: HttpServletRequest) {
        if (id == user.sessionId) throw ApiException.badRequest("CURRENT_SESSION", "Para cerrar esta sesión usa «Cerrar sesión».")
        if (!sessions.revokeOwn(user.userId, id)) throw ApiException.notFound("La sesión no existe o ya estaba cerrada.")
        audit.record(AuditActions.SESSION_REVOKED, actorId = user.userId, businessId = user.businessId, ipAddress = request.clientIp(),
            metadata = mapOf("sessionId" to id))
    }

    @PostMapping("/revoke-others")
    @Operation(summary = "Cierra todas las sesiones salvo la actual")
    fun revokeOthers(user: AuthenticatedUser, request: HttpServletRequest): RevokedSessionsResponse {
        val closed = sessions.revokeOthers(user.userId, user.sessionId)
        if (closed > 0) {
            audit.record(AuditActions.SESSION_REVOKED, actorId = user.userId, businessId = user.businessId, ipAddress = request.clientIp(),
                metadata = mapOf("closed" to closed))
        }
        return RevokedSessionsResponse(closed)
    }
}

/** Descripción breve y legible del dispositivo. No es una huella: solo ayuda a reconocer la sesión. */
object DeviceLabel {
    fun of(userAgent: String?): String {
        val ua = userAgent.orEmpty()
        if (ua.isBlank()) return "Dispositivo desconocido"
        val browser = when {
            "Edg/" in ua -> "Edge"
            "OPR/" in ua || "Opera" in ua -> "Opera"
            "SamsungBrowser" in ua -> "Samsung Internet"
            "Firefox/" in ua || "FxiOS" in ua -> "Firefox"
            "Chrome/" in ua || "CriOS" in ua -> "Chrome"
            "Safari/" in ua -> "Safari"
            else -> "Navegador"
        }
        val system = when {
            "iPhone" in ua -> "iPhone"
            "iPad" in ua -> "iPad"
            "Android" in ua -> "Android"
            "Windows" in ua -> "Windows"
            "Mac OS X" in ua || "Macintosh" in ua -> "Mac"
            "CrOS" in ua -> "ChromeOS"
            "Linux" in ua -> "Linux"
            else -> null
        }
        return if (system != null) "$browser en $system" else browser
    }
}
