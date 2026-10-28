#!/usr/bin/env bash
# Mantiene el subdominio de DuckDNS apuntando a la IP pública de este servidor.
# Lee DUCKDNS_DOMAIN (solo el nombre, sin .duckdns.org) y DUCKDNS_TOKEN del .env. Se ejecuta por cron.
set -euo pipefail
cd "$(dirname "$0")/../.."
set -a; [ -f .env ] && . ./.env; set +a
: "${DUCKDNS_DOMAIN:?Define DUCKDNS_DOMAIN en .env}"
: "${DUCKDNS_TOKEN:?Define DUCKDNS_TOKEN en .env}"

# ip vacía: DuckDNS usa la IP desde la que llega la petición.
answer="$(curl -fsS --retry 3 "https://www.duckdns.org/update?domains=${DUCKDNS_DOMAIN}&token=${DUCKDNS_TOKEN}&ip=")"
[ "$answer" = "OK" ] || { echo "DuckDNS respondió: $answer" >&2; exit 1; }
