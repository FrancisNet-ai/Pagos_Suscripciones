#!/usr/bin/env bash
# Copia de seguridad de la base de datos (pg_dump formato custom) y de los archivos subidos (imágenes y comprobantes).
set -euo pipefail
cd "$(dirname "$0")/../.."
set -a; [ -f .env ] && . ./.env; set +a

mkdir -p backups
stamp="$(date -u +%Y%m%d-%H%M%S)"
docker compose exec -T db pg_dump -U "${DB_USER:-nuvyra}" -d "${DB_NAME:-nuvyra}" --format=custom --no-owner > "backups/$stamp.dump"
docker compose exec -T backend tar -czf - -C /app/data media private > "backups/$stamp-files.tar.gz"
echo "Copias creadas: backups/$stamp.dump y backups/$stamp-files.tar.gz"
