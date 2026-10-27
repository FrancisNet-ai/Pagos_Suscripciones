#!/usr/bin/env bash
# Genera una copia de seguridad comprimida (formato custom de pg_dump) en ./backups.
set -euo pipefail
cd "$(dirname "$0")/../.."
set -a; [ -f .env ] && . ./.env; set +a

mkdir -p backups
file="backups/$(date -u +%Y%m%d-%H%M%S).dump"
docker compose exec -T db pg_dump -U "${DB_USER:-platform}" -d "${DB_NAME:-platform}" --format=custom --no-owner > "$file"
echo "Copia creada: $file"
