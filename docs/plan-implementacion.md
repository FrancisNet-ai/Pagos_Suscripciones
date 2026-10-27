# Plan de implementación

Cada fase termina con pruebas en verde y una versión utilizable de punta a punta (backend + frontend
+ persistencia). Ninguna pantalla se marca como terminada si solo existe su interfaz.

## Fase 1 — Fundamentos ✅ (este incremento)

| Entregable | Detalle |
|---|---|
| Monorepositorio | `frontend/`, `backend/`, `infrastructure/`, `docs/`, `compose.yaml`, `.env.example` |
| Backend | Spring Boot 4.1 + Kotlin, Gradle Kotlin DSL, perfiles `dev`, `test`, `prod`, Actuator, OpenAPI |
| Base de datos | PostgreSQL 17, Flyway `V1__identity_and_tenancy.sql` |
| Seguridad | Sesiones opacas en cookie HttpOnly, CSRF, CORS cerrado, cabeceras, BCrypt, rate limiting en login/registro/recuperación |
| Auth | Registro (usuario + negocio + slug + código empresarial + membresía OWNER), login, logout, refresh, `me`, recuperación de contraseña |
| Multiempresa | `CurrentBusiness` resuelto en servidor, `GET /business/me`, prueba de aislamiento A/B |
| Roles | `SUPER_ADMIN` (bootstrap por variables), `BUSINESS_OWNER`, `BUSINESS_STAFF` previsto |
| Auditoría | `audit_logs` para registro, login, logout, restablecimiento |
| Frontend | Angular 21 standalone + signals + Tailwind 4, tokens de diseño, fuentes legales, landing, login, registro, recuperación, shell del panel con datos reales del negocio |
| Infraestructura | Dockerfiles multi-stage, Nginx como proxy de mismo origen, Docker Compose |
| Pruebas | Unitarias (slug, teléfono, rate limiter), integración con Testcontainers (auth, sesión vencida, acceso no autorizado, aislamiento), unitarias de frontend |

**Pendiente explícito de Fase 1:** selección de plan y pago SaaS durante el registro (depende de los
planes de la Fase 6). Mientras tanto el negocio queda creado sin suscripción SaaS y el bloqueo por
falta de plan se activará en la Fase 6.

## Fase 2 — Negocios y productos ✅

| Entregable | Detalle |
|---|---|
| Migración | `V2__catalog_and_payment_methods.sql`: `business_payment_methods`, `products`, `capacity_allocations` |
| Configuración | Datos, descripción, color, WhatsApp, zona horaria, logo y portada; cambio de slug con verificación de disponibilidad |
| Yape / Plin | Titular declarado, celular receptor, QR opcional y habilitación, independientes entre sí |
| Productos | CRUD, `SUBSCRIPTION`/`ONE_TIME`, precio en soles, duración, disponibilidad, publicar/despublicar/archivar, imagen, búsqueda, filtros y paginación |
| Cupos | `CapacityService`: disponibilidad (ocupados + reservas vigentes), reserva con `SELECT … FOR UPDATE`, ocupación, liberación idempotente; no se reduce la capacidad por debajo del uso |
| Imágenes | Validación por firma de bytes y dimensiones, nombres aleatorios, borrado tras commit, volumen Docker `media` |
| Frontend | Configuración (3 pestañas), catálogo responsive, formulario de producto, menú con secciones activas, «Primeros pasos» con datos reales |
| Pruebas | Backend 74 (incluye concurrencia del último cupo con 8 hilos y aislamiento entre negocios); frontend 37; flujo Playwright completo |

Las reservas se crean desde el checkout (Fase 3). Una reserva vencida deja de contar automáticamente;
el proceso programado que la marca como `EXPIRED` y la gestión de transferencias tardías llegan con
las Fases 3 y 5.

## Fase 3 — Tienda y checkout ✅

| Entregable | Detalle |
|---|---|
| Migración | `V3__storefront_orders_payments.sql`: `customers`, `customer_consents`, `order_number_sequences`, `orders`, `order_items`, `customer_payments`; `capacity_allocations.order_id` |
| Tienda pública | `/tienda/{slug}` móvil, con logo, portada y color del negocio (contraste AA garantizado); solo productos publicados; agotados visibles pero no comprables; negocio suspendido sin catálogo |
| Compra sin registro | Nombre + celular + Yape/Plin + consentimientos; precio y producto resueltos en el servidor; cliente único por negocio y celular (upsert atómico); `ORD-AAAA-NNNNNN` correlativo por negocio y año; `Idempotency-Key`; rate limiting |
| Cupos | Reserva de 30 min al crear el pedido; se extiende 2 días al reportar el pago; pago tardío sin cupo → incidencia que bloquea la aprobación |
| Pago | Instrucciones con titular declarado, número copiable, QR e importe exacto; reporte de código de operación + comprobante privado; códigos duplicados detectados |
| Verificación | Aprobación con confirmación explícita, bloqueo pesimista y auditoría; rechazo con motivo y reintento del comprador; cancelación |
| WhatsApp | `MessagingProvider` + `ManualWhatsAppLinkProvider`: enlace `wa.me` con número del negocio, datos reales y estado verdadero; nunca se marca como enviado |
| Tareas | `OrderExpirationJob` cada 5 min: reservas vencidas → `EXPIRED`; pedidos sin pago tras 2 días → `EXPIRED` (idempotente, por lotes) |
| Pruebas | Backend 96 (aprobación concurrente, sobreventa, IDOR, comprobantes privados, duplicados, idempotencia); frontend 47; flujo Playwright comprador (móvil) → vendedor → comprador |

Pendiente para la Fase 4: crear la suscripción del comprador (`customer_subscriptions`) al verificar un
pago de un producto `SUBSCRIPTION` (incluidos los pedidos ya pagados en esta fase).

## Fase 4 — Gestión comercial
Dashboard con indicadores reales, clientes y clientes antiguos, suscripciones (pago ≠ activación ≠
vencimiento), historial, reportes por periodo/producto/estado/renovación.

## Fase 5 — Renovaciones y disponibilidad
Tareas programadas idempotentes (vencimientos, solicitudes de renovación, expiración de reservas),
enlaces seguros `/renovar/{token}`, respuestas sí/no, conservación del cupo, tareas de liberación,
notificaciones pendientes para envío manual.

## Fase 6 — Comercialización del SaaS
Planes configurables, suscripción y cobro SaaS con verificación por SUPER_ADMIN, límites por plan,
bloqueo de funciones sin plan vigente, panel de superadministración, suspensión/reactivación, auditoría.

## Fase 7 — Integraciones avanzadas
WhatsApp Business Platform (`WhatsAppCloudApiProvider`, plantillas, webhooks firmados), pagos
automáticos con proveedor autorizado, importación CSV/Excel con vista previa. CI/CD al estabilizar.
