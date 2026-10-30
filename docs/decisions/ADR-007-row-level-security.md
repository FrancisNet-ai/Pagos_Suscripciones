# ADR-007 — Evaluación de Row Level Security (RLS) de PostgreSQL

**Estado:** etapas 1–3 adoptadas (V17) y etapa 5 (V18), 2026-10-30; falta la etapa 4 · **Fecha:** 2026-10-28

## Contexto
El aislamiento entre negocios se aplica en la aplicación ([ADR-002](ADR-002-multiempresa-business-id.md)):
- el `business_id` sale siempre de la sesión y de `business_members`;
- los repositorios están acotados por negocio;
- hay pruebas de aislamiento por módulo.

RLS añadiría una segunda barrera en la base de datos: aunque una consulta olvidara filtrar por `business_id`,
PostgreSQL no devolvería filas de otro negocio.

Desde la cuenta con varios negocios (V15), el negocio activo puede cambiar dentro de una misma sesión.
Esto refuerza que el filtro dependa del negocio de cada petición y no del usuario.

## Qué haría falta
1. **Un rol de base de datos para la aplicación sin `BYPASSRLS`**, distinto del dueño del esquema que ejecuta
   Flyway.
2. **Políticas en cada tabla de negocio**, del tipo
   `USING (business_id = current_setting('app.business_id', true)::uuid)`.
   Para tablas hijas como `order_items`, la política iría vía su tabla padre.
3. **Fijar `SET LOCAL app.business_id` al abrir cada transacción de una petición autenticada.**
   - Lo haría un `TransactionSynchronization` o un `ConnectionPreparer` del pool.
   - Debe ser `LOCAL`: con HikariCP, una variable de sesión sobreviviría a la conexión devuelta y
     contaminaría la siguiente petición.
4. **Un contexto explícito de «plataforma»** (rol aparte o `app.scope = 'platform'`) para el código que cruza
   negocios por diseño:
   - tareas programadas: vencimientos, renovaciones, envío por WhatsApp, pendientes;
   - el webhook de Meta, que resuelve el negocio por el número;
   - la tienda pública por `slug`;
   - los enlaces por token;
   - la superadministración.

## Costos y riesgos
- **Puntos de entrada que cruzan negocios:** hay una veintena, entre jobs, webhooks, enlaces públicos y admin.
  Cada uno necesita su contexto de plataforma, y un error ahí rompe la funcionalidad, no la seguridad.
- **Errores silenciosos:** una transacción sin `app.business_id` devuelve 0 filas en lugar de fallar. Es
  difícil de diagnosticar si no se fuerza un error.
- **Pruebas:** Testcontainers debe usar el rol restringido para que las pruebas lo ejerciten.
- **Rendimiento:** el impacto es bajo porque todas las tablas ya tienen índices por `business_id`.

## Decisión
No se activa todavía. El aislamiento actual está cubierto por pruebas y no hay un hallazgo que lo
contradiga. Antes del primer despliegue con datos reales se adoptará por etapas:
1. Crear el rol `nuvyra_app` sin privilegios de esquema y conectar la aplicación con él.
2. Activar RLS primero en las tablas con datos personales: `customers`, `customer_consents`, `orders`,
   `customer_payments`, `customer_subscriptions` y `renewal_*`.
3. Fijar `SET LOCAL app.business_id` en las peticiones autenticadas, con un modo «plataforma» explícito y
   auditado para jobs, webhooks, enlaces públicos y superadministración.
4. Hacer que la ausencia de contexto falle: `current_setting('app.business_id')` sin `missing_ok`.
5. Extender la política al resto de tablas de negocio.

## Consecuencias
- El BACKLOG conserva la adopción como tarea, ahora con este plan concreto.
- Hasta entonces, el aislamiento sigue dependiendo de la aplicación y de sus pruebas.
- Toda consulta nueva debe acotarse por negocio, aunque sea SQL nativo.

## Implementación de la primera etapa (V17)
- **Rol sin privilegios en la misma conexión:** la aplicación sigue conectándose con el usuario dueño del
  esquema, que ejecuta Flyway y en Docker y en Windows es administrador de PostgreSQL. Como los
  administradores ignoran RLS, V17 crea el rol `nuvyra_app` (sin `BYPASSRLS` ni privilegios de
  administración) y la aplicación cambia a él solo cuando hace falta. No se necesitan credenciales nuevas.
- **Cuándo se aplica:** `TenantContextFilter` fija el negocio de la sesión en las rutas del panel; quedan fuera
  `/api/v1/public/**`, `/api/v1/auth/**` y `/api/v1/admin/**`.
- **Cómo se aplica:** `TenantAwareDataSource` envuelve el pool. Al entregar cada conexión ejecuta
  `set_config('role', …)` y `set_config('app.business_id', …)`:
  - con negocio, pasa a `nuvyra_app` con ese negocio;
  - sin negocio, vuelve al dueño.
  Como se fija cada vez, una conexión reutilizada no arrastra el negocio de otra petición.
- **Tablas y política:** `customers`, `customer_consents`, `orders`, `customer_payments`,
  `customer_subscriptions`, `renewal_requests`, `renewal_notifications` y `subscription_renewals`, con la
  política `tenant_isolation` (`USING` y `WITH CHECK`) para `nuvyra_app`. Sin `app.business_id`, ese rol no ve
  ninguna fila.
- **Sin negocio fijado:** las tareas programadas, los webhooks, la tienda y los enlaces públicos, la
  superadministración y Flyway siguen con el usuario dueño y el aislamiento de la aplicación. Cubrirlos es la
  etapa 4: un modo «plataforma» explícito y que falte el contexto sea un error.
- **Interruptor de emergencia:** `APP_SECURITY_ROW_LEVEL_SECURITY=false` deja de cambiar de rol sin desplegar
  otra versión.
- **Pruebas:** toda la suite corre con las políticas activas. `RowLevelSecurityIntegrationTest` comprueba que,
  con un negocio fijado, la base no devuelve, modifica ni inserta filas de otro negocio.

## Etapas pendientes
- Etapa 4: modo «plataforma» explícito y auditado para jobs, webhooks, enlaces públicos y superadministración,
  y que la ausencia de contexto falle.
- ~~Etapa 5~~ (V18): la política se extiende a:
  - `products`, `capacity_allocations`, `business_payment_methods`, `seller_tasks` y `customer_imports`;
  - `order_notifications`, `notification_preferences`, `staff_invitations`, `support_grants` y
    `order_number_sequences`;
  - `saas_subscriptions` y `saas_invoices`;
  - `order_items`, vía su pedido.

  Quedan fuera a propósito, porque el panel debe ver otros negocios o las escribe la plataforma:
  - `audit_logs`: hay registros sin negocio.
  - `business_members`: la lista de negocios de una persona.
  - `whatsapp_webhook_events`: solo la escribe el webhook.
  - `whatsapp_integrations`: un número no puede conectarse en dos negocios.
  - `saas_payments`: el número de operación no puede repetirse entre negocios, por antifraude.

