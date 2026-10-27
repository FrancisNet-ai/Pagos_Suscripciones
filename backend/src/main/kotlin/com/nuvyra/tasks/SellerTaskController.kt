package com.nuvyra.tasks

import com.nuvyra.security.CurrentBusiness
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/tasks")
@Tag(name = "Pendientes")
class SellerTaskController(private val service: SellerTaskService) {

    @GetMapping
    @Operation(summary = "Pendientes abiertos que el usuario puede atender, con responsables y plazos")
    fun list(current: CurrentBusiness, @RequestParam(defaultValue = "ALL") view: TaskView) = service.list(current, view)

    @GetMapping("/summary")
    @Operation(summary = "Totales para el menú: abiertos, asignados a mí, sin asignar y vencidos")
    fun summary(current: CurrentBusiness) = service.counts(current)

    @GetMapping("/history")
    @Operation(summary = "Historial paginado de pendientes cerrados, con autor y acción real cuando existe auditoría")
    fun history(
        current: CurrentBusiness,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ) = service.history(current, page, size)

    @PutMapping("/{id}/assignee")
    @Operation(summary = "Asigna o libera la tarea (`userId: null`). Un colaborador solo puede tomarla o soltarla")
    fun assign(current: CurrentBusiness, @PathVariable id: UUID, @RequestBody body: AssignTaskRequest) =
        service.assign(current, id, body.userId)
}
