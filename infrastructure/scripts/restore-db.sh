#!/usr/bin/env bash
# Restaura una copia creada con backup-db.sh. ATENCIÓN: reemplaza los datos actuales.
set -euo pipefail
cd "$(dirname "$0")/../.."
set -a; [ -f .env ] && . ./.env; set +a

file="${1:?Uso: restore-db.sh backups/AAAAmmdd-HHMMSS.dump}"
[ -f "$file" ] || { echo "No existe $file"; exit 1; }
read -r -p "Se reemplazarán los datos de ${DB_NAME:-platform}. Escribe RESTAURAR para continuar: " answer
[ "$answer" = "RESTAURAR" ] || { echo "Cancelado"; exit 1; }

docker compose stop backend
docker compose exec -T db pg_restore -U "${DB_USER:-platform}" -d "${DB_NAME:-platform}" --clean --if-exists --no-owner < "$file"
docker compose start backend
echo "Restauración completada"
