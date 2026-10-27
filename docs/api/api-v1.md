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
  "business": { "id": "…", "uniqueCode": "NEG-7K2M9QXA", "slug": "digital-streaming-peru", "businessName": "Digital Streaming Perú", "status": "ACTIVE" }
}
```

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

## Fases siguientes — contratos orientativos

### Renovaciones (Fase 5)

* `GET /renewals?bucket=UPCOMING|ACCEPTED|DECLINED|NO_RESPONSE` · `POST /renewals/{id}/notifications/{nid}/mark-sent`
* `GET /public/renewals/{token}` · `POST /public/renewals/{token}/response` `{ "response": "ACCEPTED|DECLINED" }`
* `POST /public/renewals/{token}/orders` — crea pedido de renovación (no ocupa un cupo nuevo).

### Administración SaaS (Fase 6)

* `GET /public/saas-plans` (landing) · `GET|POST /admin/plans` · `PUT /admin/plans/{id}`
* `GET /admin/businesses?status=` · `POST /admin/businesses/{id}/suspend` · `POST /admin/businesses/{id}/reactivate`
* `GET /admin/subscriptions` · `GET /admin/saas-payments` · `POST /admin/saas-payments/{id}/verify`
* `GET /admin/reports/overview` · `GET /admin/audit-logs`
* `GET /business/me/subscription` · `POST /business/me/subscription` `{ planId }` · `POST /business/me/saas-payments`
