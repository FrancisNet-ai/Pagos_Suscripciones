#!/usr/bin/env bash
# Prepara una VM Ubuntu nueva (Oracle Cloud Always Free, ARM) para Nuvyra. Ejecutar una vez con sudo:
#   sudo bash preparar-servidor.sh <usuario>
# Instala Docker, abre los puertos 80 y 443 en el firewall de la VM, crea /opt/nuvyra
# y programa la copia diaria y la actualización de DuckDNS. Se puede repetir sin efectos duplicados.
set -euo pipefail
user="${1:?Uso: sudo bash preparar-servidor.sh <usuario>}"
[ "$(id -u)" -eq 0 ] || { echo "Ejecuta con sudo" >&2; exit 1; }
id "$user" >/dev/null

# Docker desde el repositorio oficial (incluye el plugin de Compose).
if ! command -v docker >/dev/null; then
  apt-get update
  apt-get install -y ca-certificates curl
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  chmod a+r /etc/apt/keyrings/docker.asc
  . /etc/os-release
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu ${VERSION_CODENAME} stable" \
    > /etc/apt/sources.list.d/docker.list
  apt-get update
  apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
fi
usermod -aG docker "$user"

# Las imágenes Ubuntu de Oracle bloquean todo salvo SSH en iptables, además de la Security List de la VCN.
apt-get install -y iptables-persistent
for port in 80 443; do
  iptables -C INPUT -p tcp --dport "$port" -j ACCEPT 2>/dev/null || iptables -I INPUT 5 -p tcp --dport "$port" -j ACCEPT
done
iptables -C INPUT -p udp --dport 443 -j ACCEPT 2>/dev/null || iptables -I INPUT 5 -p udp --dport 443 -j ACCEPT
netfilter-persistent save

install -d -o "$user" -g "$user" -m 750 /opt/nuvyra /opt/nuvyra/releases /opt/nuvyra/backups

# Copia diaria 03:17 (hora de Lima = 08:17 UTC) y DuckDNS cada 30 minutos.
cat > /etc/cron.d/nuvyra <<CRON
SHELL=/bin/bash
17 8 * * * $user [ -x /opt/nuvyra/current/infrastructure/scripts/backup-offsite.sh ] && /opt/nuvyra/current/infrastructure/scripts/backup-offsite.sh >> /opt/nuvyra/backups/backup.log 2>&1
*/30 * * * * $user [ -x /opt/nuvyra/current/infrastructure/scripts/duckdns.sh ] && /opt/nuvyra/current/infrastructure/scripts/duckdns.sh >> /opt/nuvyra/backups/duckdns.log 2>&1
CRON
chmod 644 /etc/cron.d/nuvyra
echo "Servidor listo. Cierra la sesión SSH y vuelve a entrar para usar docker sin sudo."
