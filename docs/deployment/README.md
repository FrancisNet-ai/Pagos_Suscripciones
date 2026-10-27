# Despliegue y operación

## Local con Docker Compose
```bash
cp .env.example .env      # completar valores
docker compose up --build
```
* Aplicación: http://localhost:8080 (Nginx sirve Angular y reenvía `/api` al backend).
* PostgreSQL persiste en el volumen `pgdata`.

## Producción (resumen)
1. Servidor con Docker y un proxy TLS delante (Caddy, Traefik o Nginx con Let's Encrypt) que termine HTTPS
   y reenvíe al contenedor `web`.
2. Variables: `SPRING_PROFILES_ACTIVE=prod`, credenciales de base de datos propias, `APP_PUBLIC_BASE_URL=https://tudominio.com`,
   `APP_SESSION_COOKIE_SECURE=true`, SMTP.
3. Las migraciones Flyway se aplican al arrancar el backend.
4. Health check: `GET /actuator/health`.
5. Logs en stdout (formato JSON en `prod`), recogidos por Docker.

## Copias de seguridad
```bash
infrastructure/scripts/backup-db.sh            # genera backups/AAAAmmdd-HHMMSS.dump
infrastructure/scripts/restore-db.sh <archivo> # restaura sobre la base configurada
```
Programar `backup-db.sh` diariamente (cron) y copiar los archivos fuera del servidor. Probar la
restauración periódicamente en un entorno aislado.

## Política de actualizaciones
Actualizar imágenes base y dependencias mensualmente; parches de seguridad de inmediato. Desplegar
primero en un entorno de pruebas con una copia de datos anonimizada.
