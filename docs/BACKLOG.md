# Pendientes y decisiones técnicas

## Decisiones vigentes
* Nombre del producto: **Nuvyra** («Tu negocio, bajo control.»), sustituye al nombre provisional anterior. Paquete `com.nuvyra`, base de datos y proyecto Compose `nuvyra`. Pendiente: verificar marca y dominio antes del lanzamiento y renombrar el repositorio a `nuvyra-platform`.
* [ADR-001](decisions/ADR-001-monolito-modular.md) Monolito modular.
* [ADR-002](decisions/ADR-002-multiempresa-business-id.md) Multiempresa por `business_id` resuelto en servidor.
* [ADR-003](decisions/ADR-003-sesiones-cookie-httponly.md) Sesiones opacas en cookie HttpOnly + CSRF.
* [ADR-004](decisions/ADR-004-tipografias.md) Playfair Display / Bodoni Moda en lugar de Voga / Bodoni FLF hasta tener licencia.
* [ADR-005](decisions/ADR-005-pagos-manuales-yape-plin.md) Verificación manual de Yape/Plin.
* La raíz del repositorio cumple el papel de la carpeta `digital-saas/` de la especificación.
* El estado «incidencia» de un pedido se modela con `orders.incident_reason` (no como estado aparte) para no mezclar el ciclo de pago con la revisión manual.
* «Por vencer» en el panel = 3 días (primer recordatorio); en los filtros de clientes y suscripciones = 7 días para planificar.
* Al vencer o cancelar una suscripción el cupo pasa a `RELEASE_PENDING` y solo se libera cuando el vendedor confirma que retiró el acceso.
* La suspensión administrativa se guarda en `businesses.status` y no en el contrato SaaS: reactivar no altera las fechas pagadas. Por eso `saas_subscriptions` no usa el estado `SUSPENDED`.
* Sin plan vigente el panel queda en solo lectura (interceptor central), salvo «Mi plan» y las operaciones que se deben a compradores que ya pagaron.
* Un cambio de plan empieza al verificar el pago y no prorratea los días restantes; renovar el mismo plan continúa desde el vencimiento.
* Las funciones de un plan se guardan como columnas booleanas (`feature_*`) en lugar de JSONB: son pocas, tipadas y consultables en SQL.
* WhatsApp: la plataforma opera una app de Meta (secreto y token de verificación del webhook); cada negocio aporta su número, su token y sus plantillas. El envío automático exige autorización de recordatorios del cliente; los clientes importados no la tienen.
* La importación guarda la vista previa y aplica exactamente esas filas; cada fila se escribe en su propia transacción.
* Los avisos de renovación se preparan para envío manual: abrir WhatsApp no cuenta como enviado; el vendedor confirma con «Ya lo envié».
* El token de renovación es un HMAC de la solicitud con `APP_RENEWAL_TOKEN_SECRET`; cambiar el secreto invalida los enlaces ya enviados.
* El enlace del pedido usa un token aleatorio de 256 bits (solo se guarda su hash). Un reintento con la misma `Idempotency-Key` emite un enlace nuevo para el mismo pedido.

## Pendientes
- [ ] Tareas del vendedor (`seller_tasks`) como entidad propia; hoy los pendientes se derivan del estado (cupos por liberar, avisos por enviar, pagos por verificar).
- [ ] *Embedded Signup* de Meta para conectar WhatsApp sin copiar identificadores ni tokens (requiere app verificada como Tech Provider).
- [ ] Plantillas de WhatsApp para confirmaciones de pedido y de pago (hoy solo recordatorios y despedida).
- [ ] Pagos automáticos: solo si Yape/Plin u otro proveedor autorizado ofrece una API oficial de verificación.
- [ ] Despliegue continuo (CD) cuando exista un entorno de producción.
- [ ] Reembolsos (`REFUNDED`): definir el flujo manual del vendedor antes de habilitarlo.
- [ ] Playwright como suite versionada en el repositorio (hoy los flujos E2E se ejecutan como scripts de verificación).
- [ ] Migrar imágenes a almacenamiento de objetos (S3 compatible) si se escala a varias instancias.
- [ ] Generar miniaturas/WebP en el servidor para reducir peso en móviles.
- [ ] Varios negocios por cuenta (hoy un usuario pertenece a un solo negocio, como titular o colaborador).
- [ ] Permisos más finos para colaboradores (p. ej., verificar pagos sin ver ingresos del pedido).
- [ ] Acceso de soporte a datos de compradores con motivo y auditoría (hoy el SUPER_ADMIN no tiene ningún acceso a ellos).
- [ ] Comprobantes electrónicos SUNAT para los cobros SaaS (los cobros `PLAN-…` no lo son).
- [ ] Configurar SMTP real para correos de recuperación en producción (`SPRING_MAIL_*`).
- [ ] Redactar términos comerciales, política de privacidad y política de retención (Ley 29733) — requiere revisión legal.
- [ ] Evaluar Row Level Security de PostgreSQL como defensa en profundidad.
- [ ] Bloqueo distribuido de tareas programadas y rate limiting compartido si se usan varias instancias.
- [ ] Copias de seguridad automatizadas en el servidor de producción (script base en `infrastructure/scripts`).
- [ ] Adquirir licencias de Voga y Bodoni FLF si se desea la identidad tipográfica original.
