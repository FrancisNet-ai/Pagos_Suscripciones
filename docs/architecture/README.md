# Arquitectura técnica

Plataforma SaaS multiempresa para que vendedores independientes administren la venta de
suscripciones, productos y servicios digitales, cobrando a sus compradores mediante Yape y Plin.

## 1. Visión general

```
                     ┌────────────────────────────────────────────┐
  Navegador ───────► │ Proxy inverso (HTTPS, cabeceras, gzip)     │
  (Angular SPA)      │   /          → archivos estáticos Angular  │
                     │   /api/v1/*  → backend Spring Boot         │
                     └───────────────┬────────────────────────────┘
                                     │ mismo origen (cookies HttpOnly + CSRF)
                     ┌───────────────▼────────────────────────────┐
                     │ Monolito modular Kotlin / Spring Boot      │
                     │  auth · security · users · businesses ·    │
                     │  saas · products · customers · orders ·    │
                     │  payments · subscriptions · capacity ·     │
                     │  renewals · notifications · whatsapp ·     │
                     │  reports · admin · common                  │
                     │  + tareas programadas (Spring Scheduling)  │
                     └───────────────┬────────────────────────────┘
                                     │ JDBC / Flyway
                     ┌───────────────▼────────────────────────────┐
                     │ PostgreSQL (UUID, FK, TIMESTAMPTZ, índices)│
                     └────────────────────────────────────────────┘
```

* **Un único despliegue** (monolito modular). No hay Redis, colas ni microservicios: no existe una
  necesidad demostrable todavía (ver [ADR-001](../decisions/ADR-001-monolito-modular.md)).
* **Mismo origen**: el proxy sirve la SPA y la API bajo el mismo dominio. Así las cookies de sesión
  pueden ser `HttpOnly; Secure; SameSite=Lax` y CORS queda cerrado por defecto.
* **Dos sistemas económicos aislados**: facturación SaaS (módulo `saas`, tablas `saas_*`) y ventas
  del vendedor (módulos `orders`, `payments`, `subscriptions`; tablas `orders`, `customer_payments`,
  `customer_subscriptions`). No comparten tablas, servicios ni reportes.

## 2. Versiones elegidas

| Pieza | Versión | Motivo |
|---|---|---|
| Java | 21 LTS | LTS soportado por Spring Boot 4 |
| Kotlin | 2.3.x (gestionado por Spring Boot) | Requerido por Spring Boot 4 |
| Spring Boot | 4.1.x | Línea estable vigente |
| Gradle | Kotlin DSL + wrapper | Reproducible |
| PostgreSQL | 17 | Estable, `gen_random_uuid()` nativo |
| Flyway | gestionado por Spring Boot | Migraciones versionadas |
| Angular | 21 (LTS) | Versión estable con soporte vigente, standalone + signals + zoneless |
| Tailwind CSS | 4.x | Integración con Angular vía PostCSS |

## 3. Módulos del backend

Paquete raíz `com.platform`. Cada módulo agrupa su `Controller`, `Service`, `Repository`,
`Entity` y `DTO`. Un módulo solo accede a otro mediante su **servicio público**, nunca a su
repositorio, para evitar dependencias circulares y mantener límites claros.

| Módulo | Responsabilidad | Fase |
|---|---|---|
| `common` | Errores (ProblemDetail), utilidades de teléfono/slug/tiempo, entidad base, rate limiting | 1 |
| `security` | Cadena de filtros, sesiones por cookie, CSRF, CORS, resolución del negocio actual | 1 |
| `auth` | Registro, login, logout, rotación de sesión, recuperación de contraseña | 1 |
| `users` | Cuentas de vendedores y superadministradores | 1 |
| `businesses` | Negocio, miembros, slug, código empresarial, personalización | 1–2 |
| `audit` (en `common`) | Registro de auditoría de acciones sensibles | 1 |
| `products` | Catálogo por negocio, tipos SUBSCRIPTION / ONE_TIME | 2 |
| `capacity` | Cupos, reservas y liberaciones con bloqueo pesimista | 2–5 |
| `customers` | Compradores por negocio, consentimientos, clientes antiguos | 3–4 |
| `orders` | Pedidos públicos, numeración `ORD-AAAA-NNNNNN`, snapshots | 3 |
| `payments` | Métodos Yape/Plin por negocio, reporte y verificación manual, `PaymentProvider` | 3 |
| `whatsapp` | Enlaces `wa.me`, `MessagingProvider` | 3 / 7 |
| `subscriptions` | Suscripciones del comprador, activación, vencimiento | 4 |
| `reports` | Indicadores del vendedor (solo su `business_id`) | 4 |
| `renewals` | Solicitudes, tokens, respuestas, recordatorios | 5 |
| `notifications` | Notificaciones pendientes / enviadas, preferencias | 5 |
| `saas` | Planes, suscripciones SaaS, facturas y cobros SaaS, límites | 6 |
| `admin` | Panel SUPER_ADMIN, reportes de plataforma, suspensión | 6 |

## 4. Multiempresa (tenancy)

Modelo **base de datos compartida, esquema compartido**, con `business_id` en todas las tablas de
datos de negocio (ver [ADR-002](../decisions/ADR-002-multiempresa-business-id.md)).

Reglas implementadas desde la Fase 1:

1. El negocio actual **nunca** se toma de la petición (ni de la URL, ni de un header, ni del body).
   Se resuelve en el servidor a partir del usuario autenticado y su fila activa en
   `business_members` (`CurrentBusinessResolver`).
2. Los controladores administrativos reciben un parámetro `CurrentBusiness` resuelto por el servidor.
3. Los repositorios exponen métodos `findByIdAndBusinessId(...)`; un recurso de otro negocio
   responde **404** (no 403) para no revelar su existencia.
4. El `slug` solo localiza tiendas públicas; no otorga ningún permiso.
5. Las pruebas de integración verifican explícitamente que el vendedor A no ve ni modifica datos
   del vendedor B.

Preparado para `BUSINESS_STAFF`: `business_members` ya tiene `role` (`OWNER`, `STAFF`). Si un usuario
llegara a pertenecer a varios negocios, el cliente enviará un `X-Business-Id` que el servidor
**validará contra `business_members`** antes de usarlo.

## 5. Autenticación y sesiones

Ver [ADR-003](../decisions/ADR-003-sesiones-cookie-httponly.md).

* Sesión opaca: token aleatorio de 256 bits en cookie `HttpOnly; Secure; SameSite=Lax; Path=/`.
  En la base solo se guarda su **SHA-256** (`user_sessions.token_hash`).
* Expiración por inactividad (por defecto 12 h) y absoluta (por defecto 30 días). `POST /auth/refresh`
  rota el token.
* Logout revoca la sesión en la base. Cambiar o restablecer la contraseña revoca todas las sesiones.
* CSRF: patrón *double submit cookie* de Spring Security (`XSRF-TOKEN` → header `X-XSRF-TOKEN`), que
  Angular `HttpClient` envía automáticamente.
* Contraseñas con BCrypt (`DelegatingPasswordEncoder`, permite migrar de algoritmo).
* El comprador final **no** se autentica: los endpoints `/api/v1/public/**` son anónimos y están
  protegidos con rate limiting, validación estricta y tokens no adivinables cuando corresponde.

## 6. Roles

| Rol | Dónde vive | Authority de Spring |
|---|---|---|
| `SUPER_ADMIN` | `users.platform_role` | `ROLE_SUPER_ADMIN` |
| `BUSINESS_OWNER` | `business_members.role = OWNER` | `ROLE_BUSINESS_OWNER` |
| `BUSINESS_STAFF` | `business_members.role = STAFF` (futuro) | `ROLE_BUSINESS_STAFF` |
| Comprador final | sin cuenta | anónimo |

El superadministrador inicial se crea al arrancar solo si se definen las variables
`APP_BOOTSTRAP_SUPER_ADMIN_EMAIL` y `APP_BOOTSTRAP_SUPER_ADMIN_PASSWORD`.

## 7. Tiempo y fechas

* Columnas `TIMESTAMPTZ`; la JVM y Hibernate trabajan en UTC (`Instant`).
* Zona horaria de referencia configurable (`APP_TIME_ZONE`, por defecto `America/Lima`) y también por
  negocio (`businesses.time_zone`) para cálculos de "día", "mes" y vencimientos.

## 8. Concurrencia e idempotencia

* Bloqueo optimista (`@Version`) en entidades editables.
* Bloqueo pesimista (`SELECT … FOR UPDATE`) sobre la fila del producto al reservar cupos (Fase 2–3).
* Verificación de pagos con transición de estado condicional (`UPDATE … WHERE status = 'PENDING_VERIFICATION'`)
  para impedir aprobaciones dobles.
* Tareas programadas idempotentes: cada una selecciona por estado + fecha con índices, procesa por
  lotes y usa restricciones únicas (p. ej. una notificación por tipo y solicitud) para no duplicar.

## 9. Integraciones: real vs. pendiente

| Funcionalidad | MVP (real) | Futuro (requiere credenciales / autorización) |
|---|---|---|
| Cobro a compradores | Yape/Plin con **verificación manual** del vendedor | `PaymentProvider` con pasarela autorizada |
| WhatsApp | Enlace `wa.me` prellenado (el usuario confirma el envío) y, si el negocio conecta su número, WhatsApp Business Platform (Cloud API) con plantillas y webhooks firmados | Alta guiada con *Embedded Signup* de Meta |
| Recordatorios | Envío automático por WhatsApp Business a clientes con autorización; el resto, envío manual desde el panel | — |
| Correo de recuperación | SMTP configurable; en desarrollo el enlace se escribe en el log | Proveedor transaccional |
| Cobro SaaS | Registro y verificación manual por SUPER_ADMIN | Pasarela de suscripciones |
| Almacenamiento de imágenes | Disco local / volumen Docker con validación de tipo y tamaño | Almacenamiento de objetos (S3 compatible) |

Nada de lo anterior se simula: si una integración no está configurada, la interfaz lo indica.

## 10. Documentos relacionados

* [Modelo de datos](../database/modelo-datos.md)
* [API REST v1](../api/api-v1.md)
* [Rutas del frontend](rutas-frontend.md)
* [Sistema visual](sistema-visual.md)
* [Seguridad y riesgos](seguridad.md)
* [Plan de implementación](../plan-implementacion.md)
* [Pendientes y decisiones](../BACKLOG.md)
