# Pendientes y decisiones técnicas

## Decisiones vigentes
* [ADR-001](decisions/ADR-001-monolito-modular.md) Monolito modular.
* [ADR-002](decisions/ADR-002-multiempresa-business-id.md) Multiempresa por `business_id` resuelto en servidor.
* [ADR-003](decisions/ADR-003-sesiones-cookie-httponly.md) Sesiones opacas en cookie HttpOnly + CSRF.
* [ADR-004](decisions/ADR-004-tipografias.md) Playfair Display / Bodoni Moda en lugar de Voga / Bodoni FLF hasta tener licencia.
* [ADR-005](decisions/ADR-005-pagos-manuales-yape-plin.md) Verificación manual de Yape/Plin.
* La raíz del repositorio cumple el papel de la carpeta `digital-saas/` de la especificación.
* El estado «incidencia» de un pedido se modela con `orders.incident_reason` (no como estado aparte) para no mezclar el ciclo de pago con la revisión manual.
* «Por vencer» en el panel = 3 días (primer recordatorio); en los filtros de clientes y suscripciones = 7 días para planificar.
* Al vencer o cancelar una suscripción el cupo pasa a `RELEASE_PENDING` y solo se libera cuando el vendedor confirma que retiró el acceso.
* Los avisos de renovación se preparan para envío manual: abrir WhatsApp no cuenta como enviado; el vendedor confirma con «Ya lo envié».
* El token de renovación es un HMAC de la solicitud con `APP_RENEWAL_TOKEN_SECRET`; cambiar el secreto invalida los enlaces ya enviados.
* El enlace del pedido usa un token aleatorio de 256 bits (solo se guarda su hash). Un reintento con la misma `Idempotency-Key` emite un enlace nuevo para el mismo pedido.

## Pendientes
- [ ] Importación masiva de clientes desde CSV/Excel con vista previa (Fase 7).
- [ ] Tareas del vendedor (`seller_tasks`) como entidad propia; hoy los pendientes se derivan del estado (cupos por liberar, avisos por enviar, pagos por verificar).
- [ ] Envío automático de avisos de renovación con WhatsApp Business Platform (Fase 7); hoy el envío es manual.
- [ ] Reembolsos (`REFUNDED`): definir el flujo manual del vendedor antes de habilitarlo.
- [ ] Playwright como suite versionada en el repositorio (hoy los flujos E2E se ejecutan como scripts de verificación).
- [ ] Fase 6: aplicar `product_limit` del plan SaaS al crear productos.
- [ ] Migrar imágenes a almacenamiento de objetos (S3 compatible) si se escala a varias instancias.
- [ ] Generar miniaturas/WebP en el servidor para reducir peso en móviles.
- [ ] Fase 6: selección de plan y pago SaaS en el registro; bloqueo por falta de suscripción vigente.
- [ ] Configurar SMTP real para correos de recuperación en producción (`SPRING_MAIL_*`).
- [ ] Redactar términos comerciales, política de privacidad y política de retención (Ley 29733) — requiere revisión legal.
- [ ] Evaluar Row Level Security de PostgreSQL como defensa en profundidad.
- [ ] Bloqueo distribuido de tareas programadas y rate limiting compartido si se usan varias instancias.
- [ ] CI/CD (GitHub Actions) al alcanzar una versión estable.
- [ ] Copias de seguridad automatizadas en el servidor de producción (script base en `infrastructure/scripts`).
- [ ] Adquirir licencias de Voga y Bodoni FLF si se desea la identidad tipográfica original.
