# Modelo de datos (PostgreSQL)

Convenciones:

* Claves primarias `UUID` (`DEFAULT gen_random_uuid()`, la aplicación también puede asignarlas).
* Fechas `TIMESTAMPTZ` (UTC). Fechas de calendario puras como `DATE` solo cuando no hay hora.
* Importes `NUMERIC(12,2)` en soles (`PEN`); nunca `float`.
* Estados como `VARCHAR` con `CHECK` (más fácil de migrar que tipos `ENUM` de PostgreSQL).
* `version BIGINT` para bloqueo optimista en tablas editables.
* Toda tabla de datos de negocio lleva `business_id` con FK e índice compuesto que empieza por `business_id`.
* Migraciones Flyway incrementales por fase: `V1__` identidad y multiempresa, `V2__` productos, etc.

## Diagrama entidad–relación

```mermaid
erDiagram
    users ||--o{ business_members : "pertenece"
    users ||--o{ user_sessions : "tiene"
    users ||--o{ password_reset_tokens : "solicita"
    businesses ||--o{ business_members : "tiene"
    users ||--o{ businesses : "es dueño (owner_id)"

    saas_plans ||--o{ saas_subscriptions : "contratado en"
    businesses ||--o{ saas_subscriptions : "contrata"
    saas_subscriptions ||--o{ saas_invoices : "cobra"
    saas_invoices ||--o{ saas_payments : "se paga con"

    businesses ||--o{ products : "publica"
    businesses ||--o{ customers : "atiende"
    businesses ||--o{ business_payment_methods : "cobra con"
    customers ||--o{ customer_consents : "autoriza"
    customers ||--o{ orders : "realiza"
    orders ||--|{ order_items : "contiene"
    products ||--o{ order_items : "snapshot de"
    orders ||--o{ customer_payments : "se paga con"
    business_payment_methods ||--o{ customer_payments : "recibe"
    customers ||--o{ customer_subscriptions : "tiene"
    products ||--o{ customer_subscriptions : "de"
    orders ||--o| customer_subscriptions : "origina"
    products ||--o{ capacity_allocations : "cupos"
    customer_subscriptions ||--o{ capacity_allocations : "ocupa"
    customer_subscriptions ||--o{ renewal_requests : "renovación"
    renewal_requests ||--o{ renewal_notifications : "avisos"
    customers ||--o{ notification_preferences : "preferencias"
    businesses ||--o{ audit_logs : "auditoría"
    users ||--o{ audit_logs : "actor"
```

## Tablas

### Identidad y multiempresa (Fase 1 — `V1__identity_and_tenancy.sql`)

| Tabla | Columnas clave | Restricciones / índices |
|---|---|---|
| `users` | `id`, `full_name`, `email`, `password_hash`, `phone`, `platform_role` (`NULL`/`SUPER_ADMIN`), `status` (`ACTIVE`, `DISABLED`), `terms_accepted_at`, `privacy_accepted_at`, `version`, `created_at`, `updated_at` | `UNIQUE(email)`, `CHECK(email = lower(email))` |
| `businesses` | `id`, `owner_id → users`, `unique_code`, `slug`, `business_name`, `description`, `logo_url`, `cover_url`, `primary_color`, `whatsapp_number`, `time_zone`, `status` (`ACTIVE`, `SUSPENDED`), `version`, timestamps | `UNIQUE(slug)`, `UNIQUE(unique_code)`, `CHECK` formato de slug y color |
| `business_members` | `id`, `business_id`, `user_id`, `role` (`OWNER`, `STAFF`), `status` (`ACTIVE`, `REVOKED`), timestamps | `UNIQUE(business_id, user_id)`, índice por `user_id` |
| `user_sessions` | `id`, `user_id`, `token_hash` (SHA-256), `created_at`, `last_used_at`, `idle_expires_at`, `absolute_expires_at`, `revoked_at`, `ip_address`, `user_agent` | `UNIQUE(token_hash)`, índice `(user_id)` |
| `password_reset_tokens` | `id`, `user_id`, `token_hash`, `expires_at`, `used_at`, `created_at` | `UNIQUE(token_hash)` |
| `audit_logs` | `id`, `actor_id`, `business_id`, `action`, `entity_type`, `entity_id`, `metadata JSONB`, `ip_address`, `created_at` | índices `(business_id, created_at)`, `(actor_id, created_at)`; solo inserción |

### Facturación SaaS (Fase 6 — `V6__saas_billing.sql`)

| Tabla | Columnas clave |
|---|---|
| `saas_plans` | `id`, `name` (único), `description`, `price`, `billing_period` (`MONTHLY`, `QUARTERLY`, `YEARLY`), `product_limit`, `customer_limit` (`NULL` = sin límite), `admin_user_limit`, `feature_reports`, `feature_reminders`, `feature_branding`, `is_active`, `sort_order`, `version` |
| `saas_subscriptions` | `id`, `business_id` (único), `plan_id`, `status` (`PENDING_PAYMENT`, `ACTIVE`, `PAST_DUE`, `CANCELLED`, `EXPIRED`), `start_at`, `expires_at`, `grace_until`, `cancelled_at` — `CHECK` de fechas por estado |
| `saas_invoices` | `id`, `invoice_number` (`PLAN-AAAA-NNNNNN`), `business_id`, `subscription_id`, `plan_id`, `plan_name_snapshot`, `billing_period`, `kind` (`NEW`, `RENEWAL`, `PLAN_CHANGE`), `amount`, `status` (`OPEN`, `PAID`, `VOID`), `period_start`, `period_end`, `paid_at`, `voided_at` — único parcial: un cobro `OPEN` por negocio |
| `saas_payments` | `id`, `business_id`, `invoice_id`, `method`, `receiver_holder_snapshot`, `receiver_phone_snapshot`, `amount`, `transaction_reference`, `receipt_key`, `status` (`PENDING_VERIFICATION`, `VERIFIED`, `REJECTED`), `reported_at`, `reported_by`, `verified_at`, `verified_by`, `rejected_at`, `rejection_reason` — únicos parciales: un pago en revisión por cobro; `(method, transaction_reference)` en revisión o verificado |
| `saas_invoice_sequences` | `year`, `last_value` |
| `platform_settings` | Fila única: `support_whatsapp`, `support_email`, `saas_grace_days` (0–30) |
| `platform_payment_methods` | `method` (`YAPE`, `PLIN`), `holder_name`, `phone`, `qr_image_url`, `is_enabled` — cuentas de la plataforma, no de los vendedores |

`businesses` añade `suspension_reason` y `suspended_at` (obligatoria si `status = 'SUSPENDED'`).

### Catálogo y cupos (Fase 2 — `V2__catalog_and_payment_methods.sql`)

| Tabla | Columnas clave | Notas |
|---|---|---|
| `products` | `id`, `business_id`, `name`, `description`, `category`, `product_type` (`SUBSCRIPTION`, `ONE_TIME`), `price`, `duration_days`, `image_url`, `capacity_enabled`, `capacity`, `publication_status` (`DRAFT`, `PUBLISHED`, `ARCHIVED`), `is_available`, `version`, timestamps | `CHECK(product_type <> 'SUBSCRIPTION' OR duration_days > 0)`, `CHECK(NOT capacity_enabled OR capacity > 0)`; índice `(business_id, publication_status)` |
| `business_payment_methods` | `id`, `business_id`, `method` (`YAPE`, `PLIN`), `holder_name`, `phone`, `qr_image_url`, `is_enabled` | `UNIQUE(business_id, method)`; titular **declarado** por el vendedor |
| `capacity_allocations` | `id`, `business_id`, `product_id`, `allocation_status` (+ `order_id` y `customer_subscription_id` en Fases 3–4) (`RESERVED`, `OCCUPIED`, `RELEASE_PENDING`, `RELEASED`, `EXPIRED`), `reserved_until`, `released_at`, `version` | Disponibilidad = `capacity − count(RESERVED vigentes + OCCUPIED + RELEASE_PENDING)` calculada bajo `SELECT … FOR UPDATE` sobre `products` |

### Tienda, pedidos y pagos (Fase 3 — `V3__storefront_orders_payments.sql`)

| Tabla | Columnas clave | Notas |
|---|---|---|
| `customers` | `id`, `business_id`, `full_name`, `phone` (E.164 `+51…`), `source` (`STOREFRONT`, `MANUAL`, `IMPORT`), `notes`, timestamps | `UNIQUE(business_id, phone)` — **sin** unicidad global de teléfono |
| `customer_consents` | `id`, `business_id`, `customer_id`, `order_id`, `purpose` (`PURCHASE_TERMS`, `DATA_PROCESSING`, `MARKETING_WHATSAPP`, `RENEWAL_REMINDERS`), `granted`, `source`, `policy_version`, `created_at`, `revoked_at` | Historial, nunca se sobrescribe |
| `order_number_sequences` | `business_id`, `year`, `last_value` | Numeración legible por negocio y año con bloqueo de fila |
| `orders` | `id`, `order_number`, `business_id`, `customer_id`, `customer_name_snapshot`, `customer_phone_snapshot`, `kind` (`PURCHASE`, `RENEWAL`), `status` (`AWAITING_PAYMENT`, `PAYMENT_REPORTED`, `PAID`, `CANCELLED`, `EXPIRED`), `total_amount`, `currency`, `public_token_hash`, `idempotency_key`, `incident_reason`, `cancellation_reason`, `paid_at`, timestamps | `UNIQUE(business_id, order_number)`, `UNIQUE(public_token_hash)`, `UNIQUE(business_id, idempotency_key)`; `renewal_request_id` en Fase 5 |
| `order_items` | `id`, `order_id`, `product_id`, `product_name_snapshot`, `product_type_snapshot`, `unit_price_snapshot`, `duration_snapshot`, `quantity` | Copia histórica inmutable |
| `customer_payments` | `id`, `business_id`, `order_id`, `payment_method_id`, `method_snapshot`, `holder_name_snapshot`, `receiver_phone_snapshot`, `amount`, `transaction_reference`, `receipt_key` (archivo privado), `status` (`PENDING`, `REPORTED`, `PENDING_VERIFICATION`, `VERIFIED`, `REJECTED`, `CANCELLED`, `REFUNDED`), `reported_at`, `verified_at`, `verified_by`, `rejection_reason`, `version` | Índice único parcial `(business_id, method_snapshot, transaction_reference) WHERE status IN ('PENDING_VERIFICATION','VERIFIED')` para detectar referencias duplicadas |

### Suscripciones y renovaciones (Fase 4 — `V4__customer_subscriptions.sql`; renovaciones Fase 5 — `V5__renewals.sql`)

| Tabla | Columnas clave | Notas |
|---|---|---|
| `customer_subscriptions` | `id`, `business_id`, `customer_id`, `product_id`, `order_id`, `origin` (`ORDER`, `MANUAL`, `IMPORT`), `product_name_snapshot`, `paid_at`, `start_at`, `expires_at`, `duration_days`, `contracted_price`, `status` (`PENDING_ACTIVATION`, `ACTIVE`, `RENEWAL_PENDING`, `EXPIRED`, `CANCELLED`, `ENDED`), `last_renewed_at`, `cancelled_at`, `cancellation_reason`, `notes`, `version` | `CHECK` de fechas según estado; `UNIQUE(order_id)`; único parcial `(business_id, customer_id, product_id, start_at)` para manuales; índice parcial de activas por `expires_at` |
| `subscription_renewals` | `id`, `business_id`, `subscription_id`, `order_id`, `renewal_request_id`, `previous_expires_at`, `new_expires_at`, `amount`, `created_at` | Historial de renovaciones; `UNIQUE(order_id)`, `CHECK(new_expires_at > previous_expires_at)` |
| `renewal_requests` | `id`, `business_id`, `subscription_id`, `customer_id`, `product_id`, `cycle_expires_at`, `token_hash`, `token_expires_at`, `response` (`NONE`, `ACCEPTED`, `DECLINED`), `status` (`OPEN`, `AWAITING_PAYMENT`, `COMPLETED`, `DECLINED`, `EXPIRED`), `responded_at`, `completed_at`, `order_id`, `version` | `UNIQUE(subscription_id, cycle_expires_at)`: una solicitud por ciclo; `UNIQUE(token_hash)` |
| `renewal_notifications` | `id`, `business_id`, `renewal_request_id`, `customer_id`, `notification_type` (`REMINDER_BEFORE`, `REMINDER_DUE_DAY`, `FAREWELL`), `channel` (`MANUAL_LINK`, `WHATSAPP_API`), `scheduled_at`, `sent_at`, `sent_by`, `delivery_status` (`PENDING`, `SENT_MANUALLY`, `SENT`, `DELIVERED`, `FAILED`, `SKIPPED`), `provider_message_id` | `UNIQUE(renewal_request_id, notification_type)` evita duplicados |
| `notification_preferences` | `id`, `business_id`, `customer_id`, `channel`, `opted_out`, `source` (`CUSTOMER`, `SELLER`) | `UNIQUE(customer_id, channel)`; exclusiones |

`businesses` añade `reminder_days_before` (0–15), `reminder_on_due_day` y `renewal_grace_days` (0–30).
`orders.renewal_request_id` es obligatorio si y solo si `kind = 'RENEWAL'`.

### Colaboradores (`V8__business_staff.sql`)

| Tabla | Columnas clave | Notas |
|---|---|---|
| `business_members` (cambios) | `permissions` (lista separada por comas: `PRODUCTS`, `ORDERS`, `CUSTOMERS`, `RENEWALS`, `REPORTS`), `revoked_at` | Un único titular activo por negocio (índice único parcial); una cuenta pertenece a un solo negocio (`UNIQUE(user_id)`) |
| `staff_invitations` | `id`, `business_id`, `email`, `full_name`, `permissions`, `token_hash` (SHA-256), `expires_at` (7 días), `status` (`PENDING`, `ACCEPTED`, `CANCELLED`), `invited_by`, `accepted_user_id`, `accepted_at` | Una invitación pendiente por correo y negocio; el enlace solo se muestra al titular al crearlo |

El límite `saas_plans.admin_user_limit` cuenta al titular, a los colaboradores activos y a las invitaciones
pendientes vigentes; las altas se serializan con un bloqueo consultivo por negocio.

### Pendientes (`V9__seller_tasks.sql`)

| Tabla | Columnas clave | Notas |
|---|---|---|
| `seller_tasks` | `id`, `business_id`, `task_type` (`VERIFY_PAYMENT`, `REVIEW_INCIDENT`, `ACTIVATE_SERVICE`, `RELEASE_ACCESS`, `SEND_RENEWAL_NOTICE`), `entity_id` (pago, suscripción, cupo o aviso), `status` (`OPEN`, `RESOLVED`), `since`, `due_at`, `assigned_to`, `assigned_at`, `resolved_at` | Una tarea abierta por necesidad (índice único parcial); sincronización idempotente con bloqueo consultivo por negocio |

### Historial de pendientes (`V10__seller_task_resolution_history.sql`)

`seller_tasks` incorpora `resolved_by` (FK a `users`), `resolution_action` y `resolution_audit_id`
(FK a `audit_logs`). La resolución auditada conserva la fecha de la acción y se confirma o revierte
en su misma transacción. No se deduce el autor de `assigned_to`; los cierres anteriores o sin
evidencia permanecen sin autor. Se indexa el historial por negocio/fecha y la búsqueda de evidencia
por negocio/tipo de entidad/entidad/fecha. Una tarea abierta no puede contener datos de resolución.

### Reembolsos (`V11__refunds.sql`)

`customer_payments` añade `refunded_at`, `refunded_by`, `refund_reason`, `refund_reference` (con `CHECK`
de fecha y motivo en estado `REFUNDED`); `orders.status` admite `REFUNDED`; `subscription_renewals`
añade `refunded_at` para el ciclo anulado.

### Confirmaciones por WhatsApp (`V12__whatsapp_order_updates.sql`)

`whatsapp_integrations` añade `order_template` y `payment_template`; `customer_consents.purpose` admite
`ORDER_UPDATES`. `order_notifications` (`order_id`, `kind` `PAYMENT_RECEIVED`/`PAYMENT_VERIFIED`, `status`
`PENDING`/`SENT`/`DELIVERED`/`FAILED`, `attempts`, fechas de envío, entrega y lectura, `provider_message_id`)
con un mensaje por tipo y pedido.

### Acceso de soporte (`V14__support_access.sql`)

`support_grants` (`business_id`, `granted_by`, `note`, `expires_at`, `revoked_at`, `revoked_by`): autorización
temporal del titular. Cada consulta del superadministrador queda en `audit_logs` (`SUPPORT_DATA_VIEWED`, con
recurso y motivo).

### Varios negocios por cuenta (`V15__multiple_businesses.sql`)

Se elimina la restricción de un negocio por cuenta (`uq_business_members_user`). `user_sessions` añade
`active_business_id`: el negocio elegido en la sesión, validado contra las membresías activas en cada petición.

### Permiso de pagos (`V13__payments_permission.sql`)

Otorga `PAYMENTS` a los colaboradores e invitaciones pendientes que tenían `ORDERS`.

### WhatsApp e importaciones (Fase 7 — `V7__whatsapp_and_imports.sql`)

| Tabla | Columnas clave | Notas |
|---|---|---|
| `whatsapp_integrations` | `business_id` (PK), `phone_number_id` (único), `waba_id`, `display_phone`, `access_token_encrypted` (AES-GCM), `reminder_template`, `farewell_template`, `template_language`, `enabled`, `status` (`UNVERIFIED`, `VERIFIED`, `ERROR`), `last_verified_at`, `last_error` | `CHECK(NOT enabled OR status = 'VERIFIED')` |
| `whatsapp_webhook_events` | `event_key` (PK: `st:<id>:<estado>` o `msg:<id>`), `business_id`, `kind`, `received_at` | Idempotencia de entregas repetidas |
| `customer_imports` | `id`, `business_id`, `file_name`, `status` (`PREVIEW`, `COMMITTED`, `DISCARDED`), `rows` JSONB, `total_rows`, `valid_rows`, `result` JSONB, `created_by`, `expires_at`, `committed_at` | La confirmación usa exactamente las filas de la vista previa |

`renewal_notifications` añade `attempts`, `failure_reason`, `delivered_at`, `read_at` y un índice único parcial por
`provider_message_id`.

## Integridad y concurrencia

* Todas las FK con `ON DELETE RESTRICT` salvo tablas dependientes puras (`order_items`,
  `user_sessions`) con `CASCADE`. No se eliminan ventas al archivar productos.
* Los importes de pedidos y pagos se copian del producto **en el servidor**.
* Transiciones de estado críticas mediante `UPDATE … WHERE id = ? AND status = ?` y comprobación de filas afectadas.
