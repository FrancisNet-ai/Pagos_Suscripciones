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
| Roles | `SUPER_ADMIN` (bootstrap por variables), `BUSINESS_OWNER`, `BUSINESS_STAFF` (colaboradores con permisos por sección) |
| Auditoría | `audit_logs` para registro, login, logout, restablecimiento |
| Frontend | Angular 21 standalone + signals + Tailwind 4, tokens de diseño, fuentes legales, landing, login, registro, recuperación, shell del panel con datos reales del negocio |
| Infraestructura | Dockerfiles multi-stage, Nginx como proxy de mismo origen, Docker Compose |
| Pruebas | Unitarias (slug, teléfono, rate limiter), integración con Testcontainers (auth, sesión vencida, acceso no autorizado, aislamiento), unitarias de frontend |

La selección de plan y el pago SaaS durante el registro se completaron en la Fase 6.

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

## Fase 4 — Gestión comercial ✅

| Entregable | Detalle |
|---|---|
| Migración | `V4__customer_subscriptions.sql` (+ relleno idempotente para pedidos ya pagados; `capacity_allocations.customer_subscription_id`) |
| Suscripciones | Creación al verificar el pago (`PENDING_ACTIVATION`), activación con fecha real, cancelación, vencimiento automático, liberación de cupo solo con confirmación del vendedor (`ENDED`) |
| Clientes | Lista con búsqueda y filtros (activos, por vencer, vencidos), detalle con suscripciones y compras, edición, registro de clientes antiguos con suscripción opcional y detección de duplicados |
| Dashboard | Ingresos confirmados del mes y del día, pendiente de verificar, clientes, suscripciones por estado, cupos por liberar, productos agotados, gráfico de 30 días, actividad reciente |
| Reportes | Por periodo, producto, estado de pedido y clientes recurrentes; dinero confirmado separado de pendiente; históricos manuales informativos |
| Pruebas | Backend 111 (incluye relleno de migración y aislamiento); frontend 52; flujo Playwright: compras → verificación → activación → cliente antiguo → panel y reportes |

## Fase 5 — Renovaciones y avisos ✅

| Entregable | Detalle |
|---|---|
| Migración | `V5__renewals.sql`: política de avisos por negocio, `renewal_requests` (una por suscripción y ciclo), `renewal_notifications` (un aviso por tipo), `notification_preferences`, `subscription_renewals`, `orders.renewal_request_id` |
| Tarea programada | `RenewalJob` idempotente por lotes: abre solicitudes N días antes del vencimiento, programa el recordatorio previo y el del día, marca `RENEWAL_PENDING`, prepara la despedida a quien rechaza y cierra las solicitudes sin respuesta al terminar el plazo de gracia (cupo → `RELEASE_PENDING`) |
| Enlace del comprador | `/renovar/{token}` (HMAC de 256 bits, solo se guarda el hash): Sí/No, pedido de renovación con Yape/Plin sin ocupar un cupo nuevo, exclusión de avisos |
| Panel | Avisos por enviar (WhatsApp `wa.me` con el mensaje listo; «Ya lo envié» lo confirma el vendedor), próximos vencimientos, aceptaron, rechazaron, sin respuesta, renovadas, cupos por liberar; configuración de días de aviso y plazo de gracia; exclusión desde la ficha del cliente |
| Reglas | Responder «Sí» no es pagar: la suscripción se extiende solo al verificar el pago (desde el vencimiento o desde hoy si ya venció); sin autorización de recordatorios el aviso se marca con advertencia y el vendedor decide |
| Reportes | Renovaciones cobradas del mes en el panel; aceptadas, rechazadas, sin respuesta y pendientes en reportes |
| Pruebas | Backend 121 (idempotencia del job, cupo conservado, token inválido, exclusión, plazo de gracia, aislamiento); frontend 56; flujo Playwright: avisos → enlace → aceptar y pagar → verificación → renovación; rechazo → despedida |

## Fase 6 — Comercialización del SaaS ✅

| Entregable | Detalle |
|---|---|
| Migración | `V6__saas_billing.sql`: `saas_plans`, `saas_subscriptions` (una por negocio), `saas_invoices` (`PLAN-AAAA-NNNNNN`, un cobro abierto por negocio), `saas_payments` (un pago en revisión por cobro, código de operación único), `platform_settings`, `platform_payment_methods`, suspensión en `businesses` |
| Planes | Creados y editados por el SUPER_ADMIN: precio, periodo (mensual, trimestral, anual), límites de productos y clientes, reportes, avisos automáticos y personalización. Nada de precios en el código |
| Cobro SaaS | El vendedor elige un plan (en el registro o en «Mi plan»), paga por Yape/Plin a las cuentas de la plataforma con titular declarado y reporta el código; el SUPER_ADMIN lo verifica. Un plan gratuito se activa al instante. Renovar continúa desde el vencimiento; un cambio de plan empieza al verificar (sin prorrateo) |
| Vigencia del plan | `SaasBillingJob` idempotente: `ACTIVE` → `PAST_DUE` (gracia configurable) → `EXPIRED`. Sin plan vigente el panel queda en solo lectura y la tienda cerrada, sin borrar datos; siguen disponibles «Mi plan», soporte y lo que se debe a compradores que ya pagaron (verificar pagos, activar, cancelar, liberar cupos, exclusiones) |
| Límites y funciones | Productos activos y clientes (la tienda no revela el plan al comprador), reportes de ventas, avisos de renovación automáticos y colores/portada; validados en el servidor |
| Superadministración | Panel propio en `/admin`: resumen con ingresos SaaS (separados de las ventas de los vendedores), pagos SaaS con comprobante auditado, negocios con suspensión/reactivación, planes, configuración general y auditoría sin datos de compradores |
| Pruebas | Backend 134 (pago ≠ activación, doble verificación, rechazo, plan gratuito, registro con plan, límites, funciones, suspensión, gracia y vencimiento, acceso de roles, auditoría minimizada); frontend 60; flujo Playwright completo |

## Fase 7 — Integraciones avanzadas ✅

| Entregable | Detalle |
|---|---|
| Migración | `V7__whatsapp_and_imports.sql`: `whatsapp_integrations` (token cifrado, un número por negocio), `whatsapp_webhook_events` (idempotencia), estado de entrega en `renewal_notifications`, `customer_imports` |
| WhatsApp Business Platform | Cada negocio conecta su número (Cloud API) con identificador, token y plantillas aprobadas. El número se verifica contra la Graph API antes de habilitar el envío; el token se cifra con AES-256-GCM y nunca se devuelve |
| Mensajes automáticos | `RenewalDispatchJob` envía la plantilla de recordatorio (con botones Sí/No y enlace de renovación) y la de despedida solo a clientes que autorizaron recordatorios y no se excluyeron, con plan que incluye avisos automáticos. Reintentos con espera ante fallos temporales; un fallo definitivo vuelve el aviso a la bandeja manual con el motivo |
| Webhooks | `/api/v1/public/webhooks/whatsapp`: alta con token de verificación, eventos con firma `X-Hub-Signature-256` e idempotencia por evento. Registra entregado/leído/fallido, aplica respuestas Sí/No por botón (solo del mismo celular; decir que sí no es pagar) y contesta en la conversación con el enlace de pago o la despedida; «BAJA» excluye al cliente |
| Importación | CSV (coma o punto y coma, UTF-8 o Windows-1252) y Excel `.xlsx`, hasta 2000 filas y 2 MB, con vista previa por fila (celular inválido o repetido, producto inexistente, fechas incoherentes, límite del plan) y confirmación con transacción por fila. Las suscripciones importadas no cuentan como ventas y ocupan cupo si están activas |
| Pagos automáticos | No aplica: Yape y Plin no ofrecen una API oficial de verificación para terceros. Se mantiene `PaymentProvider` con verificación manual |
| CI | GitHub Actions: pruebas del backend con Testcontainers y JAR, pruebas y build del frontend y suite Playwright de escritorio/móvil contra PostgreSQL desechable; ver [E2E](testing/e2e.md) |
| Pruebas | Backend 150 (cifrado, verificación con Graph API simulada, envío solo con consentimiento, reintentos y fallos, firma e idempotencia del webhook, respuestas Sí/No y BAJA, importación CSV/Excel, límites); frontend 63; flujo Playwright con Graph API simulada y webhook firmado |
