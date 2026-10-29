#!/usr/bin/env bash
# Copia diaria fuera del servidor: ejecuta backup-db.sh, cifra ambos archivos con
# BACKUP_ENCRYPTION_PASSPHRASE y los sube al bucket de Oracle Object Storage mediante
# OCI_BACKUP_PAR_URL (solicitud preautenticada de solo escritura). Conserva 7 días en el servidor.
# Para descifrar: openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -pass env:BACKUP_ENCRYPTION_PASSPHRASE -in X.enc -out X
set -euo pipefail
cd "$(dirname "$0")/../.."
set -a; [ -f .env ] && . ./.env; set +a
: "${BACKUP_ENCRYPTION_PASSPHRASE:?Define BACKUP_ENCRYPTION_PASSPHRASE en .env}"
: "${OCI_BACKUP_PAR_URL:?Define OCI_BACKUP_PAR_URL en .env}"

dir="${BACKUP_DIR:-backups}"
export BACKUP_STAMP="$(date -u +%Y%m%d-%H%M%S)"
infrastructure/scripts/backup-db.sh

for file in "$dir/$BACKUP_STAMP.dump" "$dir/$BACKUP_STAMP-files.tar.gz"; do
  openssl enc -aes-256-cbc -pbkdf2 -iter 200000 -salt -pass env:BACKUP_ENCRYPTION_PASSPHRASE -in "$file" -out "$file.enc"
  curl -fsS --retry 3 --retry-all-errors -T "$file.enc" "${OCI_BACKUP_PAR_URL%/}/$(basename "$file.enc")"
  rm -f "$file.enc"
done
find "$dir" -maxdepth 1 -type f \( -name '*.dump' -o -name '*-files.tar.gz' \) -mtime +7 -delete
echo "Copia $BACKUP_STAMP cifrada y enviada a Object Storage"
