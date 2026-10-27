# API REST v1

Base: `/api/v1`. JSON en `camelCase`. Documentación interactiva generada en `/api/docs` (Swagger UI)
y `/api/docs/openapi.json` (deshabilitada en producción salvo que se active `APP_OPENAPI_ENABLED`).

## Convenciones

* **Autenticación**: cookie de sesión `HttpOnly`. Las peticiones que modifican estado (`POST`, `PUT`,
  `PATCH`, `DELETE`) requieren el header `X-XSRF-TOKEN` con el valor de la cookie `XSRF-TOKEN`.
* **Errores**: `application/problem+json` (RFC 9457).

  ```json
  {
    "type": "about:blank",
    "title": "Datos inválidos",
    "status": 400,
    "detail": "Revisa los campos marcados.",
    "code": "VALIDATION_ERROR",
    "errors": [{ "field": "phone", "message": "Ingresa un celular peruano válido" }]
  }
  ```

  Códigos: `VALIDATION_ERROR`, `INVALID_IMAGE`, `FILE_TOO_LARGE`, `SLUG_TAKEN`, `CAPACITY_BELOW_USAGE`, `PAYMENT_METHOD_NOT_CONFIGURED`, `UNAUTHENTICATED`, `FORBIDDEN`, `NOT_FOUND`, `CONFLICT`,
  `EMAIL_ALREADY_REGISTERED`, `INVALID_CREDENTIALS`, `RATE_LIMITED`, `SUBSCRIPTION_REQUIRED`,
  `PRODUCT_SOLD_OUT`, `PAYMENT_ALREADY_PROCESSED`, `INTERNAL_ERROR`.
* **Paginación**: `?page=0&size=20&sort=createdAt,desc` → `{ "items": [...], "page": 0, "size": 20, "totalItems": 134, "totalPages": 7 }`. `size` máximo 100.
* **Recursos de otro negocio** → `404 NOT_FOUND`.
* **Idempotencia**: los `POST` públicos de pedidos aceptan `Idempotency-Key` (UUID generado por el cliente).

## Fase 1 — implementado

### Autenticación

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| `GET` | `/auth/csrf` | público | Emite la cookie `XSRF-TOKEN` (204) |
| `POST` | `/auth/register` | público | Crea usuario + negocio + membresía OWNER; inicia sesión |
| `POST` | `/auth/login` | público | Inicia sesión |
| `POST` | `/auth/refresh` | sesión | Rota el token de sesión |
| `POST` | `/auth/logout` | sesión | Revoca la sesión (204) |
| `GET` | `/auth/me` | sesión | Usuario, roles y negocio actual |
| `POST` | `/auth/password-reset` | público | Solicita enlace de recuperación (siempre 202) |
| `POST` | `/auth/password-reset/confirm` | público | Establece nueva contraseña con token |

`POST /auth/register`

```json
{
  "fullName": "Ana Torres",
  "email": "ana@ejemplo.pe",
  "password": "una-clave-segura",
  "phone": "987 654 321",
  "businessName": "Digital Streaming Perú",
  "acceptTerms": true,
  "acceptPrivacy": true
}
```

Respuesta `201` (y cookie de sesión):

```json
{
  "user": { "id": "…", "fullName": "Ana Torres", "email": "ana@ejemplo.pe", "phone": "+51987654321", "roles": ["BUSINESS_OWNER"] },
  "business": { "id": "…", "uniqueCode": "NEG-7K2M9QXA", "slug": "digital-streaming-peru", "businessName": "Digital Streaming Perú", "status": "ACTIVE",
                "access": { "…": "…" }, "role": "OWNER", "permissions": ["PRODUCTS", "ORDERS", "CUSTOMERS", "RENEWALS", "REPORTS"] }
}
```

`business.role` es `OWNER` o `STAFF`; `permissions` son las secciones habilitadas (el titular recibe todas).

`POST /auth/login` `{ "email": "…", "password": "…" }` → `200` misma forma que `/auth/me`.
Credenciales inválidas → `401 INVALID_CREDENTIALS` (mismo mensaje para correo inexistente y clave errónea).
Límite: 10 intentos / 15 min por IP y por correo → `429 RATE_LIMITED`.

`POST /auth/password-reset/confirm` `{ "token": "…", "newPassword": "…" }` → `204`; revoca todas las sesiones.

## Fase 2 — implementado

### Negocio y configuración (OWNER)

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/business/me` | Datos del negocio del usuario autenticado |
| `PUT` | `/business/me` | `{ businessName, description, primaryColor, whatsappNumber, timeZone }` |
| `GET` | `/business/me/slug-availability?slug=` | `{ slug, available, reason }` |
| `PUT` | `/business/me/slug` | `{ slug }` → `409 SLUG_TAKEN` si está en uso |
| `POST` / `DELETE` | `/business/me/logo`, `/business/me/cover` | `multipart/form-data` campo `file`: PNG, JPG o WebP, ≤ 2 MB, ≤ 4096 px |
| `GET` | `/business/me/payment-methods` | Siempre `[YAPE, PLIN]` con `configured`, `holderName`, `phone`, `qrImageUrl`, `enabled` |
| `PUT` | `/business/me/payment-methods/{yape\|plin}` | `{ holderName, phone, enabled }` |
| `POST` / `DELETE` | `/business/me/payment-methods/{method}/qr` | QR opcional; requiere método configurado (`409 PAYMENT_METHOD_NOT_CONFIGURED`) |

### Productos (OWNER)

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/products?query=&filter=ACTIVE\|PUBLISHED\|DRAFT\|ARCHIVED\|ALL&page=&size=` | Paginado; búsqueda por nombre o categoría |
| `POST` | `/products` | Crea en `DRAFT` |
| `GET` / `PUT` | `/products/{id}` | `404` si el producto es de otro negocio |
| `PATCH` | `/products/{id}/status` | `{ publicationStatus }` |
| `POST` / `DELETE` | `/products/{id}/image` | Imagen del producto |

Cuerpo de producto:

```json
{
  "name": "Servicio Premium", "description": "…", "category": "Entretenimiento",
  "productType": "SUBSCRIPTION", "price": 15.00, "durationDays": 30,
  "capacityEnabled": true, "capacity": 5, "available": true
}
```

Respuesta incluye `availability: { capacityEnabled, capacity, occupied, reserved, available, soldOut }`;
`available` es `null` cuando el producto no controla cupos. Reducir `capacity` por debajo de los cupos
en uso → `409 CAPACITY_BELOW_USAGE`.

### Archivos públicos

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/public/media/{clave}` | Imágenes públicas con nombre aleatorio, caché inmutable. Los comprobantes de pago nunca se sirven aquí. |

### Operación

| Ruta | Descripción |
|---|---|
| `/actuator/health` | Estado (liveness/readiness) para el proxy y Docker |

## Fase 3 — implementado

### Tienda pública (anónimo)

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/public/stores/{slug}` | Nombre, descripción, logo, portada, color, `acceptingOrders`, métodos habilitados. Sin IDs internos, código empresarial ni números de cobro. Negocio suspendido → `acceptingOrders: false`, sin catálogo ni contacto |
| `GET` | `/public/stores/{slug}/products` | Productos `PUBLISHED`: precio, duración, `availableSlots` (solo con cupos), `soldOut`, `purchasable` |
| `GET` | `/public/stores/{slug}/products/{id}` | `404` si no pertenece al slug o no está publicado |
| `POST` | `/public/stores/{slug}/orders` | `{ productId, customerName, phone, paymentMethod, acceptTerms, acceptDataProcessing, acceptRenewalReminders }` + header opcional `Idempotency-Key` → `201 { publicToken, orderNumber }` |

El servidor ignora cualquier precio enviado por el navegador. Errores: `PRODUCT_SOLD_OUT`, `PRODUCT_UNAVAILABLE`,
`STORE_NOT_ACCEPTING_ORDERS`, `RATE_LIMITED` (15 pedidos/15 min por IP; 6/hora por celular y negocio).

### Pedido del comprador (token aleatorio, sin cuenta)

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/public/orders/{token}` | Estado real, importe, instrucciones de pago (titular **declarado**, celular, QR, importe exacto), reserva vigente, `whatsappUrl` (`wa.me` con mensaje prellenado) |
| `POST` | `/public/orders/{token}/payment-report` | `multipart/form-data`: `transactionReference` (4–40 alfanuméricos) y `receipt` opcional (PNG/JPG/WebP ≤ 5 MB) → pago `PENDING_VERIFICATION`, pedido `PAYMENT_REPORTED`. **Nunca** `VERIFIED` |

Errores: `DUPLICATE_REFERENCE` (mismo código vigente en el negocio y método), `PAYMENT_ALREADY_REPORTED`, `ORDER_ALREADY_PAID`, `ORDER_CLOSED`.

### Ventas y verificación (OWNER)

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/orders?filter=ALL\|TO_VERIFY\|AWAITING_PAYMENT\|PAID\|CLOSED&from=&to=&productId=&query=&page=` | Fechas en la zona horaria del negocio; búsqueda por cliente, celular o número |
| `GET` | `/orders/summary` | `{ toVerify }` |
| `GET` | `/orders/{id}` | Detalle con historial de pagos, estado del cupo, incidencia y consentimiento de recordatorios |
| `POST` | `/orders/{id}/cancel` | `{ reason }` — solo sin pago verificado; libera la reserva |
| `POST` | `/payments/{id}/verify` | Bloqueo `FOR UPDATE` + transición solo desde `PENDING_VERIFICATION`; ocupa el cupo; auditado. Segunda aprobación → `409 PAYMENT_ALREADY_PROCESSED` |
| `POST` | `/payments/{id}/reject` | `{ reason }` (5–300) — el comprador puede volver a reportar |
| `GET` | `/payments/{id}/receipt` | Comprobante privado (`Cache-Control: no-store`), cada acceso auditado |

## Fase 4 — implementado

### Clientes (OWNER)

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/customers?query=&filter=ALL\|ACTIVE\|EXPIRING\|EXPIRED&page=` | Búsqueda por nombre o celular; `EXPIRING` = vence en 7 días |
| `POST` | `/customers` | `{ fullName, phone, notes, subscription? }` — cliente antiguo; `409 CUSTOMER_EXISTS` incluye `customerId` |
| `GET` / `PUT` | `/customers/{id}` | Detalle con suscripciones y pedidos; edición de nombre y notas |
| `POST` | `/customers/{id}/subscriptions` | Suscripción antigua: `{ productId, startDate, durationDays, contractedPrice, status: ACTIVE\|EXPIRED\|CANCELLED, notes }` |

Una suscripción antigua no crea pedido ni pago: su importe es histórico y **no** suma a ingresos. Si
está activa y el producto controla cupos, ocupa un cupo (`409 PRODUCT_SOLD_OUT` si no hay espacio).
Duplicados (mismo cliente, producto e inicio) → `409 DUPLICATE_SUBSCRIPTION`.

### Suscripciones (OWNER)

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/subscriptions?filter=ALL\|PENDING_ACTIVATION\|ACTIVE\|EXPIRING\|EXPIRED\|CLOSED&query=&days=7&page=` | |
| `GET` | `/subscriptions/expiring?days=3` | Activas que vencen pronto |
| `POST` | `/subscriptions/{id}/activate` | `{ startAt? }` — inicio real (pago ≠ activación); calcula el vencimiento |
| `POST` | `/subscriptions/{id}/cancel` | `{ reason }` — el cupo queda pendiente de liberar |
| `POST` | `/subscriptions/{id}/release-capacity` | Confirma que se retiró el acceso: libera el cupo (`EXPIRED` → `ENDED`) |

Al verificar el pago de un producto `SUBSCRIPTION` se crea la suscripción en `PENDING_ACTIVATION`.
`SubscriptionExpirationJob` (cada 10 min) marca `EXPIRED` las activas vencidas; el cupo pasa a
`RELEASE_PENDING` y sigue contando como ocupado hasta la confirmación del vendedor.

### Reportes (OWNER)

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/reports/dashboard` | Ingresos confirmados (hoy, mes), pendiente de verificar, clientes, suscripciones (activas, por vencer en 3 días, vencidas, por activar, cupos por liberar), productos agotados, serie de 30 días, actividad reciente |
| `GET` | `/reports/sales?from=&to=` | Confirmado vs. pendiente, por día, por producto, pedidos por estado, clientes recurrentes (≥ 2 compras pagadas), suscripciones antiguas registradas (informativo) |

Fechas y agrupaciones en la zona horaria del negocio. Ingresos = solo pagos `VERIFIED`.

## Fase 5 — implementado

### Renovación del comprador (token, sin cuenta)

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/public/renewals/{token}` | Suscripción, vencimiento, precio, respuesta y pedido vigente (si existe) |
| `POST` | `/public/renewals/{token}/response` | `{ "response": "ACCEPTED" \| "DECLINED" }`. Aceptar no renueva: solo indica intención |
| `POST` | `/public/renewals/{token}/orders` | `{ "paymentMethod": "YAPE" \| "PLIN" }` → pedido `RENEWAL` (sin reservar cupo nuevo) y su enlace `/pedido/{token}`; si ya existe uno abierto se reutiliza |
| `POST` | `/public/renewals/{token}/opt-out` | El comprador deja de recibir avisos de este negocio |

Token inválido o vencido → `404`. Rate limiting por IP.

### Renovaciones (OWNER)

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/renewals?bucket=UPCOMING\|ACCEPTED\|DECLINED\|NO_RESPONSE\|COMPLETED&page=&size=` | Solicitudes por estado |
| `GET` | `/renewals/overview` | Conteos por bandeja y avisos por enviar |
| `GET` | `/renewals/notifications` | Avisos pendientes con mensaje y enlace `wa.me` listos, y si el cliente autorizó recordatorios |
| `POST` | `/renewals/notifications/{id}/sent` | El vendedor confirma que lo envió (`SENT_MANUALLY`) |
| `POST` | `/renewals/notifications/{id}/skip` | Omitir aviso (`SKIPPED`) |
| `GET`/`PUT` | `/renewals/settings` | `{ reminderDaysBefore (0–15), reminderOnDueDay, renewalGraceDays (0–30) }` |
| `PUT` | `/customers/{id}/notifications` | `{ "optedOut": true \| false }` exclusión registrada por el vendedor |

Al verificar el pago de un pedido `RENEWAL` la suscripción se extiende desde su vencimiento (o desde
hoy si ya venció) y se registra en `subscription_renewals`. `/reports/dashboard` añade `renewalsMonth`
y `notificationsDue`; `/reports/sales` añade `renewals { confirmed, accepted, declined, noResponse, pending }`.

## Fase 6 — implementado

### Planes (público)

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/public/saas-plans` | Planes activos para la landing y el registro |

`POST /auth/register` acepta `planId` opcional: genera el cobro del plan elegido. `/auth/me` incluye
`business.access { active, block, message, subscriptionStatus, planName, expiresAt, usableUntil, features }`.

### Mi plan (OWNER, disponible aunque el plan no esté vigente)

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/billing` | Acceso, plan, uso (productos y clientes frente a límites), cobro abierto con pagos, cuentas Yape/Plin de la plataforma, soporte, planes e historial |
| `POST` | `/billing/checkout` | `{ planId }` → cobro `NEW`, `RENEWAL` o `PLAN_CHANGE` (idempotente para el mismo plan; plan gratuito se activa al instante) |
| `POST` | `/billing/invoices/{id}/payments` | Multipart `method`, `transactionReference`, `receipt?` → `PENDING_VERIFICATION`. No activa el plan |
| `POST` | `/billing/invoices/{id}/cancel` | Anula un cobro sin pagos en revisión |

Errores: `PLAN_REQUIRED` / `BUSINESS_SUSPENDED` (403) en operaciones que modifican datos sin plan vigente;
`FEATURE_NOT_IN_PLAN` (403); `PLAN_LIMIT_REACHED` (409); `PAYMENT_UNDER_REVIEW`, `PAYMENT_ALREADY_REPORTED`,
`DUPLICATE_REFERENCE`, `PLAN_UNAVAILABLE` (409). En la tienda pública un límite de clientes responde
`STORE_NOT_ACCEPTING_ORDERS` sin revelar el plan.

### Superadministración (SUPER_ADMIN)

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/admin/overview` | Vendedores por estado, pagos por verificar, ingresos SaaS del mes, históricos y de 12 meses, planes contratados |
| `GET` / `POST` / `PUT` | `/admin/plans`, `/admin/plans/{id}` | Planes (con número de negocios vigentes) |
| `GET` | `/admin/businesses?q=&filter=ALL\|ACTIVE\|NO_PLAN\|PENDING_PAYMENT\|PAST_DUE\|EXPIRED\|SUSPENDED` | Negocios con su plan (reemplaza a `/admin/subscriptions`) |
| `GET` | `/admin/businesses/{id}` | Titular, plan, uso agregado, cobros y actividad de plataforma |
| `POST` | `/admin/businesses/{id}/suspend` · `/reactivate` | `{ reason }` · sin cuerpo |
| `GET` | `/admin/saas-payments?status=` | Pagos SaaS |
| `POST` | `/admin/saas-payments/{id}/verify` · `/reject` | Verificación con bloqueo pesimista · `{ reason }` |
| `GET` | `/admin/saas-payments/{id}/receipt` | Comprobante privado (acceso auditado) |
| `GET` | `/admin/audit-logs?businessId=&action=` | Metadatos e IP solo en acciones de plataforma |
| `GET` / `PUT` | `/admin/settings` | Soporte (WhatsApp, correo) y días de gracia |
| `PUT` | `/admin/settings/payment-methods/{yape\|plin}` | Cuenta de la plataforma; `POST`/`DELETE …/qr` para el QR |

## Fase 7 — implementado

### WhatsApp Business (OWNER)

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/business/me/whatsapp` | Estado de la conexión, plantillas, último error, si el plan lo permite y si la plataforma recibe webhooks. El token nunca se devuelve |
| `PUT` | `/business/me/whatsapp` | `{ phoneNumberId, wabaId?, accessToken? (vacío = conservar), reminderTemplate, farewellTemplate?, templateLanguage, enabled }`. Verifica el número contra la Graph API; con error queda `status = ERROR` y sin habilitar. Requiere la función de avisos automáticos |
| `DELETE` | `/business/me/whatsapp` | Desconecta y borra el token (disponible aunque el plan no esté vigente) |

`GET /renewals/notifications` incluye avisos `PENDING` y `FAILED` con `deliveryStatus`, `automatic` (lo enviará
la integración; no requiere acción) y `failureReason`. `POST …/sent` y `…/skip` aceptan avisos fallidos.

### Webhook de Meta (público)

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/public/webhooks/whatsapp?hub.mode=subscribe&hub.verify_token=&hub.challenge=` | Alta del webhook: responde el `challenge` si el token coincide con `APP_WHATSAPP_VERIFY_TOKEN` |
| `POST` | `/public/webhooks/whatsapp` | Eventos con `X-Hub-Signature-256` (HMAC-SHA256 del cuerpo con `APP_WHATSAPP_APP_SECRET`); sin firma válida → `401`. `statuses` actualiza la entrega; botones `RNW:<aviso>:Y\|N` aplican la respuesta; texto «BAJA»/«STOP» excluye al cliente. Si un evento falla responde `500` para que Meta reintente (los ya procesados se omiten) |

### Importación de clientes (OWNER)

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/customers/imports` | Multipart `file` (CSV o `.xlsx`, ≤ 2 MB, ≤ 2000 filas) → vista previa con `summary` y filas (`CREATE`, `EXISTING`, `ERROR` con motivos). No crea nada |
| `GET` | `/customers/imports/{id}` | Vista previa o resultado |
| `POST` | `/customers/imports/{id}/commit` | Aplica la vista previa (válida 24 h): `result { created, subscriptionsAdded, unchanged, failed, skipped }` y resultado por fila |
| `POST` | `/customers/imports/{id}/discard` | Descarta la vista previa |

Columnas reconocidas (sin importar tildes ni mayúsculas): `nombre`*, `celular`*, `producto`, `inicio`,
`duracion_dias` o `vencimiento`, `precio`, `estado` (activa, vencida, cancelada), `notas`.

## Colaboradores — implementado

### Equipo (solo OWNER)

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/business/me/staff` | `members` (titular y colaboradores, con `permissions`, `status`, `revokedAt`), `invitations` pendientes, `seatsUsed`, `seatLimit` (`null` sin plan vigente) y `permissionOptions` |
| `POST` | `/business/me/staff/invitations` | `{ fullName, email, permissions[] }` → `201 { invitation, link, emailSent, team }`. El enlace se devuelve **solo aquí**; se envía por correo si hay SMTP. Errores: correo con cuenta existente o invitación pendiente (`VALIDATION_ERROR`), `SEAT_LIMIT_REACHED` (409) |
| `POST` | `/business/me/staff/invitations/{id}/renew` | Enlace nuevo y 7 días más; el anterior deja de funcionar |
| `DELETE` | `/business/me/staff/invitations/{id}` | Cancela la invitación y libera el lugar |
| `PUT` | `/business/me/staff/{memberId}/permissions` | `{ permissions[] }` (al menos una). Efecto inmediato en la siguiente petición del colaborador |
| `POST` | `/business/me/staff/{memberId}/revoke` | Revoca la membresía, desactiva la cuenta y cierra sus sesiones |
| `POST` | `/business/me/staff/{memberId}/reactivate` | Restituye el acceso si el plan aún tiene lugar |

El acceso del titular no se modifica (`409 OWNER_IMMUTABLE`).

### Invitación (público, token)

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/public/invitations/{token}` | Negocio, nombre, correo, secciones y vencimiento |
| `POST` | `/public/invitations/{token}/accept` | `{ fullName, phone, password, acceptTerms, acceptPrivacy }` → crea la cuenta `BUSINESS_STAFF`, inicia sesión (cookie) y responde como `/auth/me` |

Enlace usado, cancelado, vencido o inexistente → `410 INVITATION_INVALID`.

### Permisos del colaborador

| Permiso | Endpoints |
|---|---|
| (ninguno) | `GET /business/me` |
| `PRODUCTS` | `/products/**` (el listado `GET /products` también con `CUSTOMERS`) |
| `ORDERS` | `/orders/**`, `/payments/**` |
| `CUSTOMERS` | `/customers/**` (incluida la importación), `/subscriptions/**` |
| `RENEWALS` | `/renewals/**` (ajustes solo lectura), `GET /subscriptions` y `POST /subscriptions/{id}/release-capacity` |
| `REPORTS` | `/reports/**` |

Cualquier otra ruta del negocio (configuración, Yape/Plin, WhatsApp, `/billing/**`, equipo) es exclusiva del
titular → `403 STAFF_FORBIDDEN`.

## Pendientes — implementado

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/tasks?view=ALL\|MINE\|UNASSIGNED` | Sincroniza y devuelve los pendientes abiertos que el usuario puede atender (`type`, `typeLabel`, `since`, `dueAt`, `overdue`, `assignee`, cliente, producto, pedido, monto, `detail`), los totales `counts { open, mine, unassigned, overdue }` y los miembros asignables por tipo |
| `GET` | `/tasks/summary` | Solo los totales (para el menú) |
| `PUT` | `/tasks/{id}/assignee` | `{ userId }` o `{ userId: null }`. El titular asigna a cualquier miembro con acceso a ese tipo; un colaborador solo toma la tarea para sí o suelta la suya (`403`). Tarea resuelta o ajena → `404` |

Tipos y permisos: `VERIFY_PAYMENT` y `REVIEW_INCIDENT` → `ORDERS`; `ACTIVATE_SERVICE` → `CUSTOMERS`;
`RELEASE_ACCESS` → `CUSTOMERS` o `RENEWALS` (solo cuando el acceso ya terminó); `SEND_RENEWAL_NOTICE` → `RENEWALS`
(excluye los avisos que enviará la integración oficial de WhatsApp). No hay endpoint para cerrar una tarea.
