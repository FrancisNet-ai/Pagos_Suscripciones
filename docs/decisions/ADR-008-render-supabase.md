# ADR-008 — Producción gratuita en Render + Supabase

**Estado:** aceptado · **Fecha:** 2026-11-08 · Reemplaza el plan de Oracle Cloud de `produccion-gratuita.md`.

## Contexto
- **Presupuesto:** el titular no puede asumir un costo mensual fijo por ahora, y su laptop no puede ser
  servidor.
- **Oracle descartado:** Oracle Cloud «Always Free» rechazó el registro de la cuenta. El caso con su soporte se
  cerró sin explicación.
- **Lo que ya tenía el titular:** cuentas de Supabase y de Vercel conectadas a Claude Code, y una cuenta de
  Render creada con GitHub.

## Decisión
- **Render, servicio web Free (Docker):** un solo servicio con el backend y la página Angular.
  - El backend sirve la compilación de Angular (`app.web.static-dir`, `web/SpaWebConfig.kt`) con las mismas
    reglas de caché y cabeceras de seguridad que Nginx.
  - Web y API comparten dirección, así que las cookies de sesión y la protección CSRF quedan igual.
- **Supabase Free:** PostgreSQL 17 (proyecto `Nuvyra`, `us-west-2`) y Storage para archivos.
  - Render no conserva el disco entre reinicios, así que los archivos van a Supabase Storage con
    `app.storage.type=supabase`. En local, en las pruebas y en Docker Compose se usa el disco
    (`ObjectStore`).
- **Vercel no se usa:** su plan Hobby es para uso no comercial y Nuvyra cobra planes. Servir la web desde el
  backend evita además una segunda pieza.
- **Despertar el servicio:** UptimeRobot visita `/actuator/health` cada 5 minutos, para que Render no duerma el
  servicio y las tareas programadas sigan corriendo.
- **Copias:** GitHub Actions hace una copia diaria cifrada con `pg_dump`, porque el plan Free de Supabase no
  incluye copias descargables.

## Ajustes que exigió
- **V17:** el usuario `postgres` de Supabase no es superusuario. PostgreSQL 16+ no le deja asignarse con
  `GRANT` el rol que crea; hay que pedirlo al crearlo (`createrole_self_grant`).
  - Se probó con un PostgreSQL local que imita esos permisos.
  - Una base local que ya aplicó la V17 anterior verá un aviso de *checksum* de Flyway: como solo tiene datos de
    prueba, se recrea.
- **V19:** quita todo permiso a los roles de la Data API de Supabase (`anon`, `authenticated`). El proyecto se
  creó además con la Data API desactivada.
- **Memoria:** el contenedor de Render limita el backend para que entre en 512 MB (unos 440 MB medidos bajo
  carga).

## Consecuencias
- **Correo:** Render Free bloquea el SMTP saliente, así que los correos salen por la API HTTPS de Brevo
  (`APP_MAIL_BREVO_API_KEY`, plan gratuito de 300 correos al día). SMTP sigue disponible para un servidor propio.
- **Rendimiento:** limitado por el plan gratuito.
- **Migración futura:** al haber ingresos, se pasa a planes de pago de Render y Supabase sin tocar el código.
  También se puede volver a un VPS con Docker Compose (`compose.prod.yaml` sigue funcionando).
- **Infraestructura de Oracle:** sus guías y scripts quedan como alternativa para un servidor propio.
