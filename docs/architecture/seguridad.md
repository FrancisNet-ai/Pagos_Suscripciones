# Seguridad: riesgos y controles

| # | Riesgo | Control | Estado |
|---|---|---|---|
| 1 | IDOR / fuga entre negocios | `business_id` resuelto en servidor; consultas `…AndBusinessId`; 404 para recursos ajenos; pruebas A/B | Fase 1 (base) y cada módulo |
| 2 | Robo de sesión por XSS | Cookie `HttpOnly; Secure; SameSite=Lax`; nada en `localStorage`; Angular escapa por defecto; CSP en el proxy | Fase 1 |
| 3 | CSRF | Double submit cookie (`XSRF-TOKEN` / `X-XSRF-TOKEN`) en toda petición mutante, incluidos endpoints públicos | Fase 1 |
| 4 | Fuerza bruta en login | Rate limiting por IP y por correo; mensaje de error genérico; BCrypt | Fase 1 |
| 5 | Enumeración de cuentas | `password-reset` responde siempre 202; registro devuelve conflicto solo tras validar | Fase 1 |
| 6 | Tokens filtrados en BD | Se guarda SHA-256 de tokens de sesión, recuperación, pedido y renovación; el de renovación es HMAC-SHA256 con secreto de entorno | Fase 1 / 3 / 5 ✅ |
| 7 | Importes manipulados | El servidor obtiene precio y producto; snapshots en `order_items` | Fase 3 ✅ |
| 8 | Pago aprobado dos veces | Bloqueo `FOR UPDATE` del pago + transición solo desde `PENDING_VERIFICATION` + auditoría + prueba concurrente | Fase 3 ✅ |
| 9 | Voucher falso | El voucher nunca cambia el estado a `VERIFIED`; aprobación humana obligatoria | Fase 3 ✅ |
| 10 | Sobreventa de cupos | `SELECT … FOR UPDATE` sobre el producto + prueba concurrente | Fase 2–3 ✅ |
| 11 | Spam de pedidos públicos | Rate limiting por IP y teléfono, reservas con expiración, `Idempotency-Key` | Fase 3 ✅ |
| 12 | Archivos maliciosos | Lista blanca de tipos verificando *magic bytes*, tamaño máximo, nombres aleatorios, descarga autenticada de comprobantes | Fase 2–3 ✅ |
| 13 | Datos personales en logs/URLs | Sin teléfonos ni nombres en URLs; tokens opacos; logs sin cuerpos de petición | Todas |
| 14 | Secretos en el repositorio | Variables de entorno; `.env` ignorado; `.env.example` sin valores reales | Fase 1 |
| 15 | Acceso indiscriminado del SUPER_ADMIN a compradores | Endpoints admin solo con agregados; acceso a pedidos y clientes únicamente con autorización temporal del titular, motivo obligatorio y auditoría visible para él; metadatos e IP de auditoría solo en acciones de plataforma; comprobantes SaaS con acceso auditado | Fase 6 ✅ |
| 16 | Webhooks falsificados (WhatsApp) | Firma `X-Hub-Signature-256` (HMAC, comparación en tiempo constante), exento de CSRF solo ese endpoint, idempotencia por evento, respuestas aceptadas solo desde el celular del cliente | Fase 7 ✅ |
| 17 | Cabeceras inseguras | HSTS, `X-Content-Type-Options`, `Referrer-Policy`, `frame-ancestors 'none'` | Fase 1 (backend) + proxy |
| 18 | Uso sin plan pagado | Derechos calculados en el servidor (`EntitlementService`), interceptor de solo lectura, límites con bloqueo consultivo, pago SaaS verificado manualmente con bloqueo pesimista | Fase 6 ✅ |
| 19 | Credenciales de terceros en la base | Tokens de WhatsApp cifrados con AES-256-GCM (clave `APP_SECRETS_ENCRYPTION_KEY`, id del negocio como dato autenticado); nunca se devuelven ni se registran | Fase 7 ✅ |
| 21 | Colaborador fuera de su alcance | Permisos por sección validados en el servidor en cada petición (`StaffAccessInterceptor`), lo no listado es solo del titular; invitación de un solo uso (SHA-256, 7 días, renovar invalida la anterior); revocar desactiva la cuenta y cierra sesiones; límite de usuarios del plan con bloqueo consultivo | Colaboradores ✅ |
| 20 | Archivos de importación maliciosos | Tamaño ≤ 2 MB, ≤ 2000 filas, detección por firma (no por extensión), protección de POI ante *zip bombs*, sin fórmulas evaluadas | Fase 7 ✅ |
| 22 | Contraseña comprometida o sesiones olvidadas | Cambio de contraseña desde «Mi cuenta» exigiendo la actual, con límite de intentos; cierra las demás sesiones del usuario y queda en la auditoría (`PASSWORD_CHANGED`) | Seguridad ✅ |
| 23 | Despliegue con secretos de ejemplo | Con el perfil `prod` el backend no arranca si detecta claves de desarrollo, valores de ejemplo, contraseña de administrador corta, la contraseña de base de datos de desarrollo o, fuera de localhost, HTTP o cookies sin `Secure` (`ProductionSafetyCheck`) | Seguridad ✅ |
| 24 | Dependencias vulnerables | Dependabot semanal (Gradle, npm, Actions, Docker), `npm audit` en CI y grafo de Gradle enviado a GitHub; inventario en `docs/development/dependencias.md` | Seguridad ✅ |
| 25 | Contraseña robada | Verificación en dos pasos con app de autenticación (TOTP, RFC 6238): secreto cifrado con AES-256-GCM, cada código sirve una vez, ±30 s de tolerancia, 10 códigos de recuperación de un solo uso (solo su hash), desafío de inicio de sesión de 5 min y 5 intentos; obligatoria para el superadministrador en producción (`/api/v1/admin` responde `TWO_FACTOR_REQUIRED`) | Seguridad ✅ |
| 26 | Sesiones en dispositivos ajenos | «Mi cuenta» lista las sesiones vigentes (dispositivo, IP, último uso) y permite cerrar cada una o todas las demás; activar la verificación en dos pasos cierra las anteriores | Seguridad ✅ |

## Datos personales (Perú, Ley N.º 29733)

* Minimización: al comprador solo se le pide nombre y celular.
* Consentimientos registrados con finalidad, origen, versión de política y fecha (`customer_consents`).
* Comunicaciones comerciales solo con autorización y con opción de exclusión (desde el enlace de
  renovación o por el vendedor); los avisos a clientes sin autorización se marcan y no se envían solos.
* Política de retención y eliminación documentada antes de producción (pendiente en el backlog).
