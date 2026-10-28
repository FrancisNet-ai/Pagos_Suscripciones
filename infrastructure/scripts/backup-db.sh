#!/usr/bin/env bash
# Copia de seguridad de la base de datos (pg_dump formato custom) y de los archivos subidos (imágenes y comprobantes).
# BACKUP_DIR (por defecto backups/) y BACKUP_STAMP se pueden fijar desde .env o desde otro script.
set -euo pipefail
cd "$(dirname "$0")/../.."
set -a; [ -f .env ] && . ./.env; set +a

dir="${BACKUP_DIR:-backups}"
mkdir -p "$dir"
stamp="${BACKUP_STAMP:-$(date -u +%Y%m%d-%H%M%S)}"
docker compose exec -T db pg_dump -U "${DB_USER:-nuvyra}" -d "${DB_NAME:-nuvyra}" --format=custom --no-owner > "$dir/$stamp.dump"
docker compose exec -T backend tar -czf - -C /app/data media private > "$dir/$stamp-files.tar.gz"
echo "Copias creadas: $dir/$stamp.dump y $dir/$stamp-files.tar.gz"
