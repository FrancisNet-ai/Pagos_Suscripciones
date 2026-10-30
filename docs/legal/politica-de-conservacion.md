# Política de conservación y eliminación de datos

> **Borrador para revisión legal y de negocio.** La columna «Estado» distingue lo que el sistema ya hace de lo
> que falta programar. No se debe publicar un plazo como vigente si aún no está implementado.

| Dato | Plazo propuesto | Estado |
|---|---|---|
| Sesiones vencidas o cerradas | Se borran 7 días después | **Implementado** (tarea diaria) |
| Desafíos de inicio de sesión con verificación en dos pasos | Se borran 1 día después de vencer (duran 5 minutos) | **Implementado** (tarea diaria) |
| Copias de seguridad en el servidor | 7 días | **Implementado** (`backup-offsite.sh`) |
| Copias de seguridad en Object Storage | 30 días | Se configura al crear el bucket (regla de ciclo de vida) |
| Enlaces de recuperación de contraseña | Vencen a los 30 minutos; borrar a los 30 días | Vencen: implementado · Borrado: **pendiente** |
| Pedidos no pagados | Vencen a los 2 días; conservar 90 días y luego eliminar | Vencen: implementado · Eliminación: **pendiente** |
| Comprobantes de pago de compradores (imágenes) | `[Proponer 1 año desde la verificación; confirmar]` | **Pendiente** |
| Pedidos, pagos y suscripciones de compradores | Mientras la cuenta del vendedor esté activa, y `[plazo tributario a confirmar]` después | **Pendiente** |
| Clientes sin compras ni suscripciones activas | `[Proponer 2 años sin actividad; confirmar]` | **Pendiente** |
| Autorizaciones (consentimientos) | Mientras se conserve el cliente, más `[plazo para acreditar el consentimiento]` | **Pendiente** |
| Registro de auditoría | `[Proponer 2 años]` | **Pendiente** |
| Cuenta de vendedor sin plan | Solo lectura; aviso a los `[6]` meses y eliminación a los `[12]` meses sin plan | **Pendiente** |
| Pagos y comprobantes del plan SaaS | `[Plazo tributario a confirmar]` | **Pendiente** |

## Solicitudes de eliminación
- **De un comprador:** el vendedor responsable decide y Nuvyra ejecuta el borrado. Se conservan solo los datos
  que la ley obligue a mantener, por ejemplo los vinculados a pagos.
- **De un vendedor:** al cerrar la cuenta se exporta o elimina la información de su negocio, incluidos los
  datos de sus compradores, salvo obligaciones legales.

## Para implementar después de la revisión
- Una tarea programada que aplique los plazos aprobados, con un registro de qué se eliminó y cuándo.
- La exportación de los datos de un negocio y la eliminación de una cuenta desde el panel o desde soporte.
