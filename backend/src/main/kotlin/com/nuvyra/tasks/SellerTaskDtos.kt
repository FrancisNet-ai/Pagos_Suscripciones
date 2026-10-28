package com.nuvyra.tasks

import com.nuvyra.businesses.StaffPermission
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Acciones manuales que el estado de los datos exige al vendedor. [anyOf]: permisos con los que un
 * colaborador ve la tarea (el titular las ve todas). [slaHours]: plazo sugerido desde que surge.
 */
enum class TaskType(val label: String, val slaHours: Long, vararg val anyOf: StaffPermission) {
    VERIFY_PAYMENT("Verificar pago", 24, StaffPermission.PAYMENTS),
    REVIEW_INCIDENT("Revisar pago con incidencia", 12, StaffPermission.PAYMENTS),
    ACTIVATE_SERVICE("Activar servicio", 24, StaffPermission.CUSTOMERS),
    RELEASE_ACCESS("Retirar acceso y liberar cupo", 24, StaffPermission.CUSTOMERS, StaffPermission.RENEWALS),
    SEND_RENEWAL_NOTICE("Enviar aviso de renovación", 24, StaffPermission.RENEWALS),
    ;

    val sla: Duration get() = Duration.ofHours(slaHours)

    fun visibleWith(permissions: Set<StaffPermission>) = anyOf.any { it in permissions }

    companion object {
        fun visibleTo(isOwner: Boolean, permissions: Set<StaffPermission>) =
            if (isOwner) entries.toSet() else entries.filter { it.visibleWith(permissions) }.toSet()
    }
}

enum class TaskView { ALL, MINE, UNASSIGNED }

data class TaskAssignee(val userId: UUID, val fullName: String)

data class SellerTaskItem(
    val id: UUID,
    val type: TaskType,
    val typeLabel: String,
    val since: Instant,
    val dueAt: Instant,
    val overdue: Boolean,
    val assignee: TaskAssignee?,
    // Contexto para identificar el caso y abrir la pantalla donde se resuelve.
    val customerId: UUID?,
    val customerName: String?,
    val productName: String?,
    val orderId: UUID?,
    val orderNumber: String?,
    val amount: BigDecimal?,
    val subscriptionId: UUID?,
    val detail: String?,
)

data class TaskCounts(val open: Long, val mine: Long, val unassigned: Long, val overdue: Long)

/** Miembro activo al que se puede asignar una tarea de los tipos indicados. */
data class AssignableMember(val userId: UUID, val fullName: String, val owner: Boolean, val types: Set<TaskType>)

data class SellerTaskList(val items: List<SellerTaskItem>, val counts: TaskCounts, val members: List<AssignableMember>)

data class AssignTaskRequest(val userId: UUID?)

enum class TaskResolutionAction(val label: String) {
    PAYMENT_VERIFIED("Pago verificado"),
    PAYMENT_REJECTED("Pago rechazado"),
    ORDER_CANCELLED("Pedido cancelado"),
    SUBSCRIPTION_ACTIVATED("Servicio activado"),
    SUBSCRIPTION_CANCELLED("Servicio cancelado"),
    CAPACITY_RELEASED("Acceso retirado y cupo liberado"),
    NOTIFICATION_SENT("Aviso enviado manualmente"),
    NOTIFICATION_SKIPPED("Aviso omitido"),
    NOTIFICATIONS_OPT_OUT("Avisos desactivados"),
    STATE_CHANGED("Cerrado por cambio de estado"),
}

data class ResolvedSellerTask(
    val task: SellerTaskItem,
    val resolvedAt: Instant,
    val resolvedBy: TaskAssignee?,
    val resolutionAction: TaskResolutionAction,
    val resolutionLabel: String,
)
