#!/usr/bin/env bash
# Copia de seguridad de la base de datos (pg_dump formato custom) y de las imágenes subidas.
set -euo pipefail
cd "$(dirname "$0")/../.."
set -a; [ -f .env ] && . ./.env; set +a

mkdir -p backups
stamp="$(date -u +%Y%m%d-%H%M%S)"
docker compose exec -T db pg_dump -U "${DB_USER:-platform}" -d "${DB_NAME:-platform}" --format=custom --no-owner > "backups/$stamp.dump"
docker compose exec -T backend tar -czf - -C /app/data media > "backups/$stamp-media.tar.gz"
echo "Copias creadas: backups/$stamp.dump y backups/$stamp-media.tar.gz"
