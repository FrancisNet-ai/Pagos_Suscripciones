# Pendientes y decisiones técnicas

## Decisiones vigentes
* [ADR-001](decisions/ADR-001-monolito-modular.md) Monolito modular.
* [ADR-002](decisions/ADR-002-multiempresa-business-id.md) Multiempresa por `business_id` resuelto en servidor.
* [ADR-003](decisions/ADR-003-sesiones-cookie-httponly.md) Sesiones opacas en cookie HttpOnly + CSRF.
* [ADR-004](decisions/ADR-004-tipografias.md) Playfair Display / Bodoni Moda en lugar de Voga / Bodoni FLF hasta tener licencia.
* [ADR-005](decisions/ADR-005-pagos-manuales-yape-plin.md) Verificación manual de Yape/Plin.
* La raíz del repositorio cumple el papel de la carpeta `digital-saas/` de la especificación.

## Pendientes
- [ ] Fase 3: crear reservas de cupo desde el checkout y marcar reservas vencidas (`EXPIRED`) con tarea programada; transferencias tardías como incidencia.
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
