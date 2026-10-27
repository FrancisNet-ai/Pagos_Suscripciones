# Traspaso a Codex — estado del proyecto Nuvyra

Fecha: 27 de octubre de 2026. Repositorio: `FrancisNet-ai/Pagos_Suscripciones`.

## Estado

Todo está fusionado en `main` (último commit `68a936b`). No hay PR abierto ni trabajo sin subir.
Para seguir, crea una rama nueva desde `main`.

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

**Pruebas en el último estado:** 166 del backend, 79 del frontend y los flujos de Playwright
(`frontend/e2e/`) en escritorio y móvil. El CI de GitHub está en verde.

## Lo que sigue (de `docs/BACKLOG.md`)

**Se puede hacer solo con código:**
1. Plantillas de WhatsApp para confirmar pedidos y pagos (hoy solo hay recordatorios y despedida).
2. Reembolsos (`REFUNDED`): primero hay que definir el flujo manual del vendedor.
3. Miniaturas y WebP generados en el servidor.
4. Permisos más finos para colaboradores, por ejemplo verificar pagos sin ver ingresos.
5. Guardar quién resolvió cada pendiente.
6. Varios negocios por cuenta (hoy cada usuario pertenece a un solo negocio).
7. Evaluar Row Level Security de PostgreSQL.
8. Playwright en CI: la suite ya está versionada en `frontend/e2e/`, pero el CI no la ejecuta.
   Además, el BACKLOG todavía la marca como pendiente; conviene actualizar esa línea.

**Depende de algo externo:**
- Embedded Signup de Meta.
- Pagos automáticos: Yape/Plin no tienen API oficial.
- CD y copias de seguridad en producción.
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
- **Migraciones:** las nuevas empiezan en `V10__…`
  (`backend/src/main/resources/db/migration`).
- **Pendientes:** no tienen endpoint para «marcar como hecho». Se sincronizan con el estado de
  los datos en `tasks/SellerTaskService.kt`.
- **Comentarios en KDoc:** no escribas `/**` dentro de un KDoc (por ejemplo, `/api/v1/x/**`),
  porque abre un comentario anidado y rompe la compilación.

## Cómo correr las pruebas
- **Backend:** `cd backend && ./gradlew test` (usa Testcontainers, necesita Docker).
- **Frontend:** `cd frontend && npx ng test --watch=false` y `npx ng build`.
- **E2E:** con PostgreSQL, el backend (`SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun` y las
  variables de `.env.example`) y `npx ng serve` levantados, ejecuta
  `E2E_ADMIN_EMAIL=… E2E_ADMIN_PASSWORD=… npx playwright test`. Las pruebas usan el
  superadministrador local de pruebas y crean datos, así que solo deben correr en un entorno
  local desechable.

## Documentación
- API: `docs/api/api-v1.md`.
- Modelo de datos: `docs/database/modelo-datos.md`.
- Rutas del frontend: `docs/architecture/rutas-frontend.md`.
- Seguridad: `docs/architecture/seguridad.md`.
- Decisiones y pendientes: `docs/BACKLOG.md`.
- Visión general: `README.md`.
