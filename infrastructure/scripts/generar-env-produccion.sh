#!/usr/bin/env bash
# Crea el .env de producción con secretos aleatorios generados en el propio servidor.
# Uso: generar-env-produccion.sh <subdominio.duckdns.org> <correo-superadmin> [destino]
# No sobrescribe un .env existente. Muestra una sola vez la contraseña del superadministrador
# y la frase de cifrado de las copias: guárdalas en un gestor de contraseñas.
set -euo pipefail
domain="${1:?Uso: generar-env-produccion.sh <subdominio.duckdns.org> <correo-superadmin> [destino]}"
email="${2:?Falta el correo del superadministrador}"
target="${3:-/opt/nuvyra/.env}"
[ ! -e "$target" ] || { echo "Ya existe $target; no se sobrescribe." >&2; exit 1; }

admin_password="$(openssl rand -hex 12)"
backup_passphrase="$(openssl rand -hex 32)"
umask 077
cat > "$target" <<ENV
# .env de producción generado el $(date -u +%Y-%m-%d). Nunca se sube al repositorio.
COMPOSE_FILE=compose.yaml:compose.prod.yaml
APP_DOMAIN=${domain}
ACME_EMAIL=${email}
DUCKDNS_DOMAIN=${domain%%.duckdns.org}
DUCKDNS_TOKEN=

DB_NAME=nuvyra
DB_USER=nuvyra
DB_PASSWORD=$(openssl rand -hex 24)

SPRING_PROFILES_ACTIVE=prod
APP_PUBLIC_BASE_URL=https://${domain}
APP_TIME_ZONE=America/Lima
APP_RENEWAL_TOKEN_SECRET=$(openssl rand -hex 32)
APP_SECRETS_ENCRYPTION_KEY=$(openssl rand -base64 32)
APP_WHATSAPP_APP_SECRET=
APP_WHATSAPP_VERIFY_TOKEN=
APP_OPENAPI_ENABLED=false

APP_MAIL_FROM=${email}
SPRING_MAIL_HOST=
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=
SPRING_MAIL_PASSWORD=

APP_BOOTSTRAP_SUPER_ADMIN_EMAIL=${email}
APP_BOOTSTRAP_SUPER_ADMIN_PASSWORD=${admin_password}

BACKUP_DIR=$(dirname "$target")/backups
BACKUP_ENCRYPTION_PASSPHRASE=${backup_passphrase}
OCI_BACKUP_PAR_URL=
ENV

cat <<MSG
Creado $target (solo lo puede leer tu usuario).
Guarda AHORA en tu gestor de contraseñas; no se volverán a mostrar:
  Superadministrador: ${email} / ${admin_password}
  Frase de cifrado de las copias: ${backup_passphrase}
Sin esa frase no se pueden recuperar las copias si se pierde el servidor.
Falta completar en $target: DUCKDNS_TOKEN y OCI_BACKUP_PAR_URL.
MSG
