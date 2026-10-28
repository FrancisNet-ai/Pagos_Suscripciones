# Pendientes y decisiones técnicas

## Decisiones vigentes
* Nombre del producto: **Nuvyra** («Tu negocio, bajo control.»), sustituye al nombre provisional anterior. Paquete `com.nuvyra`, base de datos y proyecto Compose `nuvyra`. Pendiente: verificar marca y dominio antes del lanzamiento y renombrar el repositorio a `nuvyra-platform`.
* [ADR-001](decisions/ADR-001-monolito-modular.md) Monolito modular.
* [ADR-002](decisions/ADR-002-multiempresa-business-id.md) Multiempresa por `business_id` resuelto en servidor.
* [ADR-003](decisions/ADR-003-sesiones-cookie-httponly.md) Sesiones opacas en cookie HttpOnly + CSRF.
* [ADR-004](decisions/ADR-004-tipografias.md) Playfair Display / Bodoni Moda en lugar de Voga / Bodoni FLF hasta tener licencia.
* [ADR-005](decisions/ADR-005-pagos-manuales-yape-plin.md) Verificación manual de Yape/Plin.
* [ADR-006](decisions/ADR-006-java26-node24.md) Desarrollo con Java 26 y Node 24; artefacto compatible con Java 21 LTS y comprobaciones en ambos JDK.
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
* Pendientes (`seller_tasks`): se abren y se resuelven al sincronizar con el estado de los datos; no existe «marcar como hecha», porque la tarea solo se cierra cuando la acción se completa en su pantalla. Lo que aportan es el responsable, desde cuándo esperan y un plazo sugerido (12–24 h).
* Historial de pendientes: las acciones auditadas cierran la tarea dentro de la misma transacción, conservando el autor real, la acción y la fecha. El responsable asignado no acredita la resolución. Los cierres sin evidencia y los registros anteriores se muestran sin autor, nunca se atribuyen al lector de la bandeja.
* El enlace del pedido usa un token aleatorio de 256 bits (solo se guarda su hash). Un reintento con la misma `Idempotency-Key` emite un enlace nuevo para el mismo pedido.

## Pendientes
- [ ] *Embedded Signup* de Meta para conectar WhatsApp sin copiar identificadores ni tokens (requiere app verificada como Tech Provider).
- [x] Confirmaciones de pago recibido y pago verificado por WhatsApp (plantillas opcionales del negocio, solo a compradores que lo autorizan al comprar).
- [ ] Pagos automáticos: solo si Yape/Plin u otro proveedor autorizado ofrece una API oficial de verificación.
- [ ] Despliegue continuo (CD) cuando exista un entorno de producción.
- [x] Reembolsos manuales (`REFUNDED`): el titular registra la devolución que ya hizo por Yape/Plin; el pago deja de contar como ingreso, la compra cancela la suscripción y la renovación devuelve la vigencia anterior.
- [x] Playwright versionado y ejecutado en CI, con PostgreSQL desechable, recorridos de escritorio/móvil e informe de diagnóstico.
- [ ] Migrar imágenes a almacenamiento de objetos (S3 compatible) si se escala a varias instancias.
- [x] Versión reducida (640 px) de las imágenes públicas al subirlas: JPEG para fotos, PNG si hay transparencia; los listados la piden con `?size=sm`.
- [ ] Codificar las versiones reducidas en WebP (Java no escribe WebP sin bibliotecas nativas; evaluar libwebp o un servicio de imágenes).
- [x] Historial paginado de pendientes resueltos con autor y evidencia de auditoría, limitado al negocio y los permisos actuales.
- [x] Varios negocios por cuenta: crear otro negocio, cambiar de negocio en la sesión y unirse como colaborador con una cuenta existente.
- [x] Permiso propio para aprobar o rechazar pagos (`PAYMENTS`), separado de atender pedidos (`ORDERS`); los ingresos siguen en `REPORTS` y los reembolsos son del titular.
- [x] Acceso de soporte a pedidos y clientes: solo con autorización temporal del titular (1–72 h, revocable), motivo obligatorio en cada consulta y registro visible para el titular.
- [ ] Comprobantes electrónicos SUNAT para los cobros SaaS (los cobros `PLAN-…` no lo son).
- [ ] Configurar SMTP real para correos de recuperación en producción (`SPRING_MAIL_*`).
- [ ] Redactar términos comerciales, política de privacidad y política de retención (Ley 29733) — requiere revisión legal.
- [ ] Adoptar Row Level Security por etapas antes de producción (evaluado en [ADR-007](decisions/ADR-007-row-level-security.md)).
- [ ] Bloqueo distribuido de tareas programadas y rate limiting compartido si se usan varias instancias.
- [ ] Copias de seguridad automatizadas en el servidor de producción (script base en `infrastructure/scripts`).
- [ ] Adquirir licencias de Voga y Bodoni FLF si se desea la identidad tipográfica original.

### Seguridad de cuentas
- [x] Cambiar la contraseña desde el panel («Mi cuenta»), cerrando las demás sesiones.
- [x] El backend no arranca en producción con secretos de ejemplo o de desarrollo.
- [x] Revisión automática de dependencias (Dependabot y `npm audit` en CI). Falta activar las alertas en *Settings → Code security* del repositorio.
- [ ] Verificación en dos pasos (TOTP) opcional para titulares y obligatoria para el superadministrador.
- [ ] Lista de sesiones abiertas con dispositivo y opción de cerrar cada una.
- [ ] Verificar el correo al registrarse (requiere SMTP real).
- [ ] Aviso por correo de inicio de sesión desde un dispositivo nuevo (requiere SMTP real).
- [ ] Rechazar contraseñas filtradas (consulta por prefijo de hash, sin enviar la contraseña).
