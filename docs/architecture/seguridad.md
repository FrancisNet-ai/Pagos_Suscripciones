# Seguridad: riesgos y controles

| # | Riesgo | Control | Estado |
|---|---|---|---|
| 1 | IDOR / fuga entre negocios | `business_id` resuelto en servidor; consultas `…AndBusinessId`; 404 para recursos ajenos; pruebas A/B | Fase 1 (base) y cada módulo |
| 2 | Robo de sesión por XSS | Cookie `HttpOnly; Secure; SameSite=Lax`; nada en `localStorage`; Angular escapa por defecto; CSP en el proxy | Fase 1 |
| 3 | CSRF | Double submit cookie (`XSRF-TOKEN` / `X-XSRF-TOKEN`) en toda petición mutante, incluidos endpoints públicos | Fase 1 |
| 4 | Fuerza bruta en login | Rate limiting por IP y por correo; mensaje de error genérico; BCrypt | Fase 1 |
| 5 | Enumeración de cuentas | `password-reset` responde siempre 202; registro devuelve conflicto solo tras validar | Fase 1 |
| 6 | Tokens filtrados en BD | Se guarda SHA-256 de tokens de sesión, recuperación y renovación | Fase 1 / 5 |
| 7 | Importes manipulados | El servidor obtiene precio y producto; snapshots en `order_items` | Fase 3 ✅ |
| 8 | Pago aprobado dos veces | Bloqueo `FOR UPDATE` del pago + transición solo desde `PENDING_VERIFICATION` + auditoría + prueba concurrente | Fase 3 ✅ |
| 9 | Voucher falso | El voucher nunca cambia el estado a `VERIFIED`; aprobación humana obligatoria | Fase 3 ✅ |
| 10 | Sobreventa de cupos | `SELECT … FOR UPDATE` sobre el producto + prueba concurrente | Fase 2–3 ✅ |
| 11 | Spam de pedidos públicos | Rate limiting por IP y teléfono, reservas con expiración, `Idempotency-Key` | Fase 3 ✅ |
| 12 | Archivos maliciosos | Lista blanca de tipos verificando *magic bytes*, tamaño máximo, nombres aleatorios, descarga autenticada de comprobantes | Fase 2–3 ✅ |
| 13 | Datos personales en logs/URLs | Sin teléfonos ni nombres en URLs; tokens opacos; logs sin cuerpos de petición | Todas |
| 14 | Secretos en el repositorio | Variables de entorno; `.env` ignorado; `.env.example` sin valores reales | Fase 1 |
| 15 | Acceso indiscriminado del SUPER_ADMIN a compradores | Endpoints admin solo con agregados; acceso a detalle de compradores con motivo y auditoría | Fase 6 |
| 16 | Webhooks falsificados (WhatsApp) | Verificación de firma `X-Hub-Signature-256`, idempotencia por `message_id` | Fase 7 |
| 17 | Cabeceras inseguras | HSTS, `X-Content-Type-Options`, `Referrer-Policy`, `frame-ancestors 'none'` | Fase 1 (backend) + proxy |

## Datos personales (Perú, Ley N.º 29733)

* Minimización: al comprador solo se le pide nombre y celular.
* Consentimientos registrados con finalidad, origen, versión de política y fecha (`customer_consents`).
* Comunicaciones comerciales solo con autorización y con opción de exclusión.
* Política de retención y eliminación documentada antes de producción (pendiente en el backlog).
