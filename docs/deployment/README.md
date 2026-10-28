# Despliegue y operación

## Local con Docker Compose
```bash
cp .env.example .env      # completar valores
docker compose up --build
```
* Aplicación: http://localhost:8080 (Nginx sirve Angular y reenvía `/api` al backend).
* PostgreSQL persiste en el volumen `pgdata`; las imágenes subidas (logos, portadas, productos, QR) en `media`.

## Producción gratuita
Plan elegido (Oracle Cloud Always Free + DuckDNS) y lista de avance:
[produccion-gratuita.md](produccion-gratuita.md). Puesta en marcha del servidor, despliegue automático y
copias en Object Storage: [oracle-servidor.md](oracle-servidor.md).

## Producción (resumen)
1. Servidor con Docker y un proxy TLS delante (Caddy, Traefik o Nginx con Let's Encrypt) que termine HTTPS
   y reenvíe al contenedor `web`.
2. Variables: `SPRING_PROFILES_ACTIVE=prod`, credenciales de base de datos propias, `APP_PUBLIC_BASE_URL=https://tudominio.com`,
   `APP_SESSION_COOKIE_SECURE=true`, SMTP y `APP_RENEWAL_TOKEN_SECRET` (obligatorio, ≥ 32 caracteres,
   `openssl rand -base64 48`; cambiarlo invalida los enlaces de renovación ya enviados) y `APP_SECRETS_ENCRYPTION_KEY`
   (obligatorio, `openssl rand -base64 32`; cifra los tokens de WhatsApp de los negocios).
   Para WhatsApp Business: crea una app en Meta, define `APP_WHATSAPP_APP_SECRET` y `APP_WHATSAPP_VERIFY_TOKEN`, y
   registra como webhook `https://tudominio.com/api/v1/public/webhooks/whatsapp` (campo `messages`).
3. Para crear el superadministrador inicial define una vez `APP_BOOTSTRAP_SUPER_ADMIN_EMAIL` y
   `APP_BOOTSTRAP_SUPER_ADMIN_PASSWORD` (≥ 12 caracteres); después configura en `/admin` las cuentas Yape/Plin
   de la plataforma, el soporte y los planes. Sin planes publicados los vendedores no pueden activar su cuenta.
4. Las migraciones Flyway se aplican al arrancar el backend.
5. Health check: `GET /actuator/health`.
6. Logs en stdout (formato JSON en `prod`), recogidos por Docker.

## Copias de seguridad
```bash
infrastructure/scripts/backup-db.sh            # genera backups/AAAAmmdd-HHMMSS.dump y …-files.tar.gz
infrastructure/scripts/restore-db.sh <archivo.dump> [archivo-files.tar.gz]  # restaura base e imágenes
```
En el servidor de producción, `backup-offsite.sh` corre cada día (cron de `preparar-servidor.sh`):
cifra ambos archivos y los envía a Object Storage. Probar la restauración periódicamente en un entorno
aislado.

## Política de actualizaciones
Actualizar imágenes base y dependencias mensualmente; parches de seguridad de inmediato. Desplegar
primero en un entorno de pruebas con una copia de datos anonimizada.
