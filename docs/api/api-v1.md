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

  Códigos: `VALIDATION_ERROR`, `UNAUTHENTICATED`, `FORBIDDEN`, `NOT_FOUND`, `CONFLICT`,
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

### Negocio

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| `GET` | `/business/me` | OWNER | Datos del negocio del usuario autenticado |

### Operación

| Ruta | Descripción |
|---|---|
| `/actuator/health` | Estado (liveness/readiness) para el proxy y Docker |

## Fases siguientes — contratos orientativos

### Negocio y configuración (Fase 2)

* `PUT /business/me` — nombre, descripción, color, WhatsApp, zona horaria.
* `PUT /business/me/slug` — cambio de slug con verificación de disponibilidad.
* `POST /business/me/logo`, `POST /business/me/cover` — `multipart/form-data`, PNG/JPEG/WebP, ≤ 2 MB.
* `GET|PUT /business/me/payment-methods/{YAPE|PLIN}`, `POST /business/me/payment-methods/{method}/qr`.

### Productos (Fase 2)

* `GET /products?query=&status=&page=` · `POST /products` · `GET /products/{id}` · `PUT /products/{id}`
* `PATCH /products/{id}/status` `{ "publicationStatus": "PUBLISHED|DRAFT|ARCHIVED" }`
* `POST /products/{id}/image`
* `GET /products/{id}/availability` → `{ capacityEnabled, capacity, occupied, reserved, available }`

### Tienda pública y checkout (Fase 3)

* `GET /public/stores/{slug}` → datos públicos del negocio (sin IDs internos sensibles).
* `GET /public/stores/{slug}/products` → productos `PUBLISHED`, con `soldOut` y disponibilidad solo si hay cupos.
* `POST /public/stores/{slug}/orders` `{ productId, customerName, phone, paymentMethod, acceptTerms, acceptDataProcessing }`
  → `201 { orderNumber, publicToken, amount, paymentInstructions, status, reservedUntil }`.
  El precio lo calcula el servidor; el producto debe pertenecer al `slug`.
* `GET /public/orders/{publicToken}` → estado real del pedido.
* `POST /public/orders/{publicToken}/payment-report` `{ transactionReference }` + comprobante opcional → `PENDING_VERIFICATION`.
* `GET /public/orders/{publicToken}/whatsapp-link` → `{ url, message }` generado con datos reales.

### Ventas y pagos (Fase 3)

* `GET /orders?from=&to=&productId=&status=&page=` · `GET /orders/{id}`
* `POST /payments/{id}/verify` · `POST /payments/{id}/reject` `{ reason }` — transición condicional, auditada.
* `GET /payments/{id}/receipt` — descarga autenticada del comprobante (nunca URL pública).

### Clientes y suscripciones (Fase 4)

* `GET /customers?query=&filter=ALL|ACTIVE|EXPIRING|EXPIRED&page=` · `POST /customers` · `GET /customers/{id}` · `PUT /customers/{id}`
* `GET /subscriptions?status=&page=` · `POST /subscriptions/manual` · `POST /subscriptions/{id}/activate` `{ startAt }`
* `GET /subscriptions/expiring?days=3`
* `GET /reports/dashboard` · `GET /reports/sales?from=&to=&groupBy=day|product|status`

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
