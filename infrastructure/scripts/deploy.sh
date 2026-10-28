#!/usr/bin/env bash
# Despliega una versión en el servidor (lo invoca el workflow «Despliegue»).
# Uso: deploy.sh <archivo.tar.gz generado con git archive>
# Cada versión vive en /opt/nuvyra/releases/<fecha>; /opt/nuvyra/current apunta a la activa y
# todas comparten /opt/nuvyra/.env. Si la nueva no responde, vuelve a levantar la anterior.
set -euo pipefail
archive="${1:?Uso: deploy.sh <archivo.tar.gz>}"
base="${NUVYRA_HOME:-/opt/nuvyra}"
[ -f "$base/.env" ] || { echo "Falta $base/.env: créalo con generar-env-produccion.sh" >&2; exit 1; }

release="$base/releases/$(date -u +%Y%m%d-%H%M%S)"
mkdir -p "$release"
tar -xzf "$archive" -C "$release"
ln -sfn "$base/.env" "$release/.env"
previous="$(readlink -f "$base/current" 2>/dev/null || true)"

# COMPOSE_FILE (en .env) se resuelve contra el directorio actual: se entra a cada versión.
compose_in() {
  local dir="$1"; shift
  (cd "$dir" && docker compose "$@")
}

healthy() {
  for _ in $(seq 1 60); do
    if compose_in "$1" exec -T web wget -qO- http://127.0.0.1/actuator/health 2>/dev/null | grep -q '"UP"'; then
      return 0
    fi
    sleep 5
  done
  return 1
}

compose_in "$release" up -d --build --remove-orphans
if healthy "$release"; then
  ln -sfn "$release" "$base/current"
  rm -f "$archive"
  # Conserva las tres últimas versiones y limpia imágenes sin uso.
  ls -1dt "$base"/releases/*/ | tail -n +4 | xargs -r rm -rf
  docker image prune -f >/dev/null
  echo "Desplegado: $release"
else
  echo "La nueva versión no respondió en 5 minutos." >&2
  compose_in "$release" logs --tail 80 backend >&2 || true
  if [ -n "$previous" ] && [ -d "$previous" ]; then
    echo "Restaurando la versión anterior: $previous" >&2
    compose_in "$previous" up -d --build --remove-orphans
    if healthy "$previous"; then echo "La versión anterior volvió a responder." >&2; else echo "La versión anterior tampoco responde: revisa los logs." >&2; fi
  fi
  rm -rf "$release"
  exit 1
fi
