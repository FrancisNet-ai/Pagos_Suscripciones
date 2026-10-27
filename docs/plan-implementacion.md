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

## Fase 2 — Negocios y productos
Configuración del negocio (datos, logo, portada, color, slug, WhatsApp, zona horaria), métodos Yape/Plin,
CRUD de productos con tipos `SUBSCRIPTION`/`ONE_TIME`, publicación/archivo, imágenes validadas, cupos
(`capacity_allocations`, disponibilidad con bloqueo pesimista).

## Fase 3 — Tienda y checkout
Tienda pública por slug, catálogo, compra sin registro, clientes por negocio, consentimientos, pedidos
con numeración `ORD-AAAA-NNNNNN`, reserva temporal de cupo, instrucciones Yape/Plin, reporte de pago y
comprobante, verificación/rechazo manual auditado, enlace `wa.me`, Playwright para el flujo completo.

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
