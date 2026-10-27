#!/usr/bin/env bash
# Restaura una copia creada con backup-db.sh. ATENCIÓN: reemplaza los datos actuales.
set -euo pipefail
cd "$(dirname "$0")/../.."
set -a; [ -f .env ] && . ./.env; set +a

dump="${1:?Uso: restore-db.sh backups/AAAAmmdd-HHMMSS.dump [backups/AAAAmmdd-HHMMSS-files.tar.gz]}"
media="${2:-}"
[ -f "$dump" ] || { echo "No existe $dump"; exit 1; }
[ -z "$media" ] || [ -f "$media" ] || { echo "No existe $media"; exit 1; }
read -r -p "Se reemplazarán los datos de ${DB_NAME:-nuvyra}. Escribe RESTAURAR para continuar: " answer
[ "$answer" = "RESTAURAR" ] || { echo "Cancelado"; exit 1; }

if [ -n "$media" ]; then
  docker compose exec -T backend sh -c 'rm -rf /app/data/media/* /app/data/private/* && tar -xzf - -C /app/data' < "$media"
fi
docker compose stop backend
docker compose exec -T db pg_restore -U "${DB_USER:-nuvyra}" -d "${DB_NAME:-nuvyra}" --clean --if-exists --no-owner < "$dump"
docker compose start backend
echo "Restauración completada"
