# Traspaso a Codex — estado del proyecto Nuvyra

Fecha: 28 de octubre de 2026. Repositorio: `FrancisNet-ai/Pagos_Suscripciones`.

## Estado

`main` incluye el PR #12 (todas las funciones del BACKLOG que solo dependían de código). El PR siguiente
agrega seguridad de cuentas, revisión automática de dependencias y orden del repositorio (última fila de
la tabla). Si ya se fusionó, crea una rama nueva desde `main` para seguir.

SaaS multiempresa para vendedores de suscripciones digitales en Perú, con la marca **Nuvyra**
(«Tu negocio, bajo control.»).

**Stack:**
- Frontend: Angular 21.
- Backend: Kotlin con Spring Boot 4 (Jackson 3, `tools.jackson`).
- Base de datos: PostgreSQL con migraciones Flyway.
- Infraestructura: Docker; CI con GitHub Actions.

## Qué está hecho

| Bloque | Contenido | PR |
|---|---|---|
| Fases 1–7 | Autenticación multiempresa, catálogo y cupos, tienda pública, pedidos con verificación manual de Yape/Plin, clientes y suscripciones, renovaciones, planes SaaS con panel `/admin`, WhatsApp Cloud API con webhooks firmados, importación CSV/Excel, CI | #1 |
| Marca Nuvyra | Paquete `com.nuvyra`, nombre en toda la app | #3 |
| Rediseño editorial | Rediseño del titular del proyecto | #4 |
| Colaboradores (`BUSINESS_STAFF`) | Invitación por enlace de un solo uso, permisos por sección (`PRODUCTS`, `ORDERS`, `CUSTOMERS`, `RENEWALS`, `REPORTS`), revocar o reactivar, límite de usuarios según el plan. Migración V8 | #5 |
| Pendientes (`seller_tasks`) | Página «Pendientes»: pagos por verificar, servicios por activar, cupos por liberar y avisos por enviar. Cada uno tiene responsable y plazo, y se cierra solo cuando la acción se hace. Migración V9 | #6 |
| Historial de pendientes y E2E en CI | Página `/panel/pendientes/historial` y `GET /api/v1/tasks/history`: casos cerrados con el autor real, la acción y la fecha (el responsable asignado no acredita la resolución). Playwright se ejecuta en GitHub Actions con PostgreSQL 17 desechable, en escritorio y móvil. Migración V10 | #8 |
| Desarrollo en Windows sin Docker | Java 21 o 26, Node 24, PostgreSQL nativo; guía en `docs/development/windows.md` | #9 |
| Reembolsos manuales | El titular registra la devolución que ya hizo; deja de contar como ingreso; corrige suscripción o renovación. V11 | #12 |
| Confirmaciones por WhatsApp | Pago recibido y pago verificado, solo a quien lo autoriza al comprar; plantillas opcionales. V12 | #12 |
| Imágenes reducidas | Versión de 640 px al subir (`?size=sm`) usada en tienda y catálogo | #12 |
| Permiso de pagos | `PAYMENTS` separado de `ORDERS` (atender pedidos). V13 | #12 |
| Acceso de soporte | Solo con autorización temporal del titular, motivo en cada consulta y registro visible. V14 | #12 |
| Varios negocios por cuenta | Crear otro negocio, cambiar de negocio en la sesión, unirse a otro con una cuenta existente. V15 | #12 |
| Evaluación de RLS | Plan de adopción por etapas en `docs/decisions/ADR-007-row-level-security.md` | #12 |
| Seguridad y orden | Cambio de contraseña en «Mi cuenta» (`/panel/cuenta`, `/admin/cuenta`) que cierra las demás sesiones; el perfil `prod` no arranca con secretos de ejemplo; Dependabot y `npm audit` en CI; versiones del backend en `backend/gradle/libs.versions.toml` e inventario en `docs/development/dependencias.md`; sin capturas de diseño en el repositorio | siguiente |

**Pruebas en el último estado:** 194 del backend, 91 del frontend y los recorridos de Playwright
(`frontend/e2e/`) en escritorio y móvil. El CI de GitHub los ejecuta en tres trabajos: backend, frontend y E2E.

## Lo que sigue (de `docs/BACKLOG.md`)

**Se puede hacer solo con código:**
1. Adoptar Row Level Security por etapas (plan en ADR-007).
2. Codificar las imágenes reducidas en WebP (requiere bibliotecas nativas).
3. Seguridad de cuentas: verificación en dos pasos, lista de sesiones abiertas y rechazo de contraseñas
   filtradas (detalle en el BACKLOG).

**Configuración única en GitHub:** activar *Dependency graph*, *Dependabot alerts* y *Dependabot security
updates* en *Settings → Code security*.

**Depende de algo externo:**
- Embedded Signup de Meta.
- Pagos automáticos: Yape/Plin no tienen API oficial.
- CD y copias de seguridad en producción: plan gratuito con Oracle Cloud Always Free y DuckDNS, con avance en `docs/deployment/produccion-gratuita.md`.
- SMTP real.
- Textos legales (Ley 29733).
- Comprobantes SUNAT.
- Licencias de las tipografías.
- Verificar la marca y el dominio.

## Reglas obligatorias
- La interfaz va en español, sin datos falsos ni funciones decorativas que no funcionen.
- No guardar secretos, tokens ni claves reales en el repositorio.
- No guardar credenciales de cuentas de terceros.
- El `business_id` siempre sale de la sesión, nunca del cliente.
- Un comprobante (voucher) o código nunca marca un pago como `VERIFIED`; lo aprueba una persona.
- Abrir WhatsApp no cuenta como mensaje enviado.
- No mezclar los ingresos del SaaS con las ventas del vendedor.
- No registrar a los compradores: compran sin cuenta.
- No cambiar la identidad visual sin autorización del titular del proyecto.

## Convenciones técnicas
- **Permisos de colaboradores:** cada permiso nuevo se valida en el backend, en
  `backend/src/main/kotlin/com/nuvyra/staff/StaffAccessInterceptor.kt`. Lo que no tiene regla
  ahí es solo del titular.
- **Rutas nuevas del panel:** se agregan en tres lugares: `security/SecurityConfig.kt`,
  `StaffAccessInterceptor.PATHS` y `saas/EntitlementInterceptor.PATHS`. Sin plan vigente, el
  panel queda en modo de solo lectura.
- **Migraciones:** las nuevas empiezan en `V16__…`
  (`backend/src/main/resources/db/migration`).
- **Varios negocios:** el negocio activo vive en la sesión (`user_sessions.active_business_id`) y se valida
  contra `business_members` en cada petición; nunca se toma del cliente.
- **Pendientes:** no tienen endpoint para «marcar como hecho». Se sincronizan con el estado de
  los datos en `tasks/SellerTaskService.kt`. Las acciones auditadas cierran la tarea en la misma
  transacción y guardan `resolved_by`, `resolution_action` y `resolution_audit_id`. Los cierres sin
  evidencia quedan como `STATE_CHANGED` y sin autor; nunca se atribuyen al responsable ni a quien
  consulta.
- **Dependencias:** las versiones del backend se cambian solo en `backend/gradle/libs.versions.toml`;
  toda dependencia nueva se anota en `docs/development/dependencias.md`.
- **Producción:** si agregas un secreto nuevo, súmalo a `common/config/ProductionSafetyCheck.kt` para que
  el perfil `prod` rechace valores de ejemplo.
- **Comentarios en KDoc:** no escribas `/**` dentro de un KDoc (por ejemplo, `/api/v1/x/**`),
  porque abre un comentario anidado y rompe la compilación.

## Cómo correr las pruebas
- **Backend:** `cd backend && ./gradlew test` (usa Testcontainers, necesita Docker).
- **Frontend:** `cd frontend && npx ng test --watch=false` y `npx ng build`.
- **E2E:** con PostgreSQL, el backend (`SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun` y las
  variables de `.env.example`) y `npx ng serve` levantados, ejecuta
  `E2E_ADMIN_EMAIL=… E2E_ADMIN_PASSWORD=… npx playwright test`. Las pruebas usan el
  superadministrador local de pruebas y crean datos, así que solo deben correr en un entorno
  local desechable. En CI corre en el trabajo E2E de `.github/workflows/ci.yml`; los detalles
  están en `docs/testing/e2e.md`.

## Documentación
- API: `docs/api/api-v1.md`.
- Modelo de datos: `docs/database/modelo-datos.md`.
- Rutas del frontend: `docs/architecture/rutas-frontend.md`.
- Seguridad: `docs/architecture/seguridad.md`.
- Pruebas E2E y CI: `docs/testing/e2e.md`.
- Dependencias: `docs/development/dependencias.md`.
- Decisiones y pendientes: `docs/BACKLOG.md`.
- Visión general: `README.md`.
