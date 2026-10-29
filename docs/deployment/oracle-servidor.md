# Servidor en Oracle Cloud — puesta en marcha

Guía del **paso 3** de [producción gratuita](produccion-gratuita.md). Supone que ya existen las cuentas
de Oracle Cloud y DuckDNS. Todo se hace desde la consola web de Oracle, GitHub y una terminal SSH; la
laptop solo se usa para conectarse.

**Nunca pegues en un chat, issue o commit** las claves SSH, el token de DuckDNS, el contenido del `.env`
ni las contraseñas que generen los scripts.

## Qué deja listo el repositorio

| Archivo | Para qué |
|---|---|
| `compose.prod.yaml` | Agrega Caddy (HTTPS automático) delante de la web; la web deja de publicar puertos |
| `infrastructure/caddy/Caddyfile` | Certificado de Let's Encrypt para `APP_DOMAIN` y HSTS |
| `infrastructure/scripts/preparar-servidor.sh` | Instala Docker, abre los puertos 80 y 443, crea `/opt/nuvyra` y programa las tareas (cron) |
| `infrastructure/scripts/generar-env-produccion.sh` | Crea `/opt/nuvyra/.env` con secretos aleatorios generados en el servidor |
| `infrastructure/scripts/deploy.sh` | Instala una versión, espera a que responda y, si falla, vuelve a la anterior |
| `infrastructure/scripts/backup-offsite.sh` | Copia diaria cifrada y enviada a Object Storage |
| `infrastructure/scripts/duckdns.sh` | Mantiene el subdominio apuntando a la IP del servidor |
| `.github/workflows/despliegue.yml` | Despliega al servidor cuando el CI termina en verde sobre `main` |

## 1. Crear la máquina virtual

En la consola de Oracle: **Compute → Instances → Create instance**.

1. **Image:** Canonical Ubuntu 24.04 (la variante para `aarch64`).
2. **Shape:** *Ampere* → `VM.Standard.A1.Flex`, con 4 OCPU y 24 GB de RAM. Debe decir «Always Free-eligible».
   - Si aparece «Out of capacity», reintenta más tarde o baja a 2 OCPU y 12 GB.
3. **Networking:** crea una VCN nueva con subred pública y marca «Assign a public IPv4 address».
4. **SSH keys:** «Generate a key pair for me» y descarga la **clave privada**.
   - Guárdala fuera de la carpeta del repositorio, por ejemplo en `C:\Users\<tu-usuario>\.ssh\nuvyra.key`.
5. **Boot volume:** deja el tamaño por defecto (50 GB). El límite gratuito es 200 GB en total.
6. Crea la instancia y anota su **IP pública**.

**Recomendado: fijar la IP.** En *Networking → IP Management → Reserved public IPs* convierte la IP de la
instancia en reservada. Así no cambia si la máquina se reinicia.

## 2. Abrir los puertos 80 y 443 en Oracle

En *Networking → Virtual cloud networks → (tu VCN) → Subnets → (subred pública) → Security Lists →
Default Security List → Add Ingress Rules*, agrega tres reglas con origen `0.0.0.0/0`:
- TCP, puerto 80 (Let's Encrypt y redirección a HTTPS).
- TCP, puerto 443.
- UDP, puerto 443 (HTTP/3, opcional).

El firewall interno de la VM lo abre el script del paso 4.

## 3. Apuntar DuckDNS a la IP

En https://www.duckdns.org escribe la IP pública en tu subdominio y pulsa *update ip*.

Caddy necesita que el subdominio ya apunte al servidor para obtener el certificado. Después, el cron
del servidor lo mantiene actualizado.

## 4. Preparar el servidor

Desde la laptop (PowerShell o CMD), dentro de la carpeta del repositorio:

```bat
scp -i C:\Users\<tu-usuario>\.ssh\nuvyra.key infrastructure\scripts\preparar-servidor.sh infrastructure\scripts\generar-env-produccion.sh ubuntu@<IP>:~
ssh -i C:\Users\<tu-usuario>\.ssh\nuvyra.key ubuntu@<IP>
```

Ya en el servidor:

```bash
sudo bash preparar-servidor.sh ubuntu
exit    # vuelve a entrar con ssh para que el grupo docker tenga efecto
```

Luego, de nuevo dentro del servidor:

```bash
bash generar-env-produccion.sh <subdominio>.duckdns.org <correo-del-proyecto>
```

El script muestra **una sola vez** la contraseña del superadministrador y la frase de cifrado de las
copias. Guárdalas en un gestor de contraseñas: sin la frase no se pueden recuperar las copias.

Después completa el token de DuckDNS. Se copia de la página de DuckDNS y queda solo en el servidor:

```bash
nano /opt/nuvyra/.env    # DUCKDNS_TOKEN=…   (Ctrl+O para guardar, Ctrl+X para salir)
```

## 5. Conectar GitHub Actions con el servidor

En el servidor, crea una clave exclusiva para el despliegue:

```bash
ssh-keygen -t ed25519 -f ~/.ssh/nuvyra_deploy -N "" -C github-actions
cat ~/.ssh/nuvyra_deploy.pub >> ~/.ssh/authorized_keys
cat ~/.ssh/nuvyra_deploy            # copiar a DEPLOY_SSH_KEY y luego borrar con: rm ~/.ssh/nuvyra_deploy
for f in /etc/ssh/ssh_host_*_key.pub; do echo "<IP> $(cut -d' ' -f1,2 "$f")"; done   # copiar a DEPLOY_KNOWN_HOSTS
```

En GitHub, *Settings → Secrets and variables → Actions*:

| Tipo | Nombre | Valor |
|---|---|---|
| Secret | `DEPLOY_HOST` | IP pública del servidor |
| Secret | `DEPLOY_USER` | `ubuntu` |
| Secret | `DEPLOY_SSH_KEY` | Contenido completo de `~/.ssh/nuvyra_deploy` (clave privada) |
| Secret | `DEPLOY_KNOWN_HOSTS` | Las líneas que imprimió el `for` |
| Variable | `APP_DOMAIN` | `<subdominio>.duckdns.org` |
| Variable | `DEPLOY_ENABLED` | `true` |

## 6. Primer despliegue

1. En GitHub, *Actions → Despliegue → Run workflow*.
2. La primera vez compila el backend y la web en el servidor, lo que tarda varios minutos.
3. Al terminar, abre `https://<subdominio>.duckdns.org/actuator/health`. Debe responder `{"status":"UP"}`.
4. Inicia sesión en `/ingresar` con el superadministrador. En **Mi cuenta**, cambia su contraseña y activa la
   verificación en dos pasos con una app de autenticación (es obligatoria para entrar a `/admin`). Guarda los
   códigos de recuperación en tu gestor de contraseñas.
5. En `/admin`, configura las cuentas Yape/Plin de la plataforma, el soporte y los planes.
6. Vacía en `/opt/nuvyra/.env` las líneas `APP_BOOTSTRAP_SUPER_ADMIN_EMAIL` y
   `APP_BOOTSTRAP_SUPER_ADMIN_PASSWORD`: ya no se necesitan.

Desde entonces, cada fusión en `main` con el CI en verde se despliega sola.

Si la versión nueva no responde en 5 minutos, `deploy.sh` vuelve a levantar la anterior y el workflow
queda en rojo. Las migraciones de base de datos que ya se aplicaron no se revierten.

## 7. Copias de seguridad en Object Storage

En la consola de Oracle: **Storage → Buckets → Create bucket**.

1. Nombre `nuvyra-backups`, nivel *Standard*, visibilidad privada.
2. En el bucket, *Lifecycle Policy Rules*: borrar objetos con más de 30 días.
3. En el bucket, *Pre-Authenticated Requests → Create*:
   - Tipo: *Bucket*.
   - Acceso: *Permit object writes* (solo escritura).
   - Vencimiento: un año. Anota la fecha, porque habrá que renovarla.
4. Copia la URL que aparece (solo se muestra una vez) en `OCI_BACKUP_PAR_URL` de `/opt/nuvyra/.env`.
5. Prueba la copia:

   ```bash
   /opt/nuvyra/current/infrastructure/scripts/backup-offsite.sh
   ```

   Debe terminar con «Copia … cifrada y enviada a Object Storage». Los dos archivos `.enc` aparecen en el
   bucket.

La copia corre sola cada día a las 03:17 de Lima. El registro queda en `/opt/nuvyra/backups/backup.log`.

## 8. Probar una restauración

Hazlo al menos una vez, idealmente en una VM de prueba:

```bash
cd /opt/nuvyra/current
export BACKUP_ENCRYPTION_PASSPHRASE='<frase guardada>'
for f in AAAAmmdd-HHMMSS.dump AAAAmmdd-HHMMSS-files.tar.gz; do
  openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -pass env:BACKUP_ENCRYPTION_PASSPHRASE -in "$f.enc" -out "$f"
done
infrastructure/scripts/restore-db.sh AAAAmmdd-HHMMSS.dump AAAAmmdd-HHMMSS-files.tar.gz
```

## 9. Alerta si se cae

En UptimeRobot, crea un monitor *HTTP(s)* hacia `https://<subdominio>.duckdns.org/actuator/health` cada
5 minutos, con aviso a tu correo.

## Salida a internet del servidor

El backend consulta `https://api.pwnedpasswords.com` para rechazar contraseñas filtradas: solo envía los 5 primeros
caracteres del hash y nunca la contraseña. Oracle permite la salida por defecto; si algún día se restringe, abre ese
destino por HTTPS. Si no responde, las contraseñas se aceptan igual.

## Operación diaria

- **Ver el estado:** `cd /opt/nuvyra/current && docker compose ps`.
- **Ver los logs:** `docker compose logs -f backend` (Ctrl+C para salir).
- **Reiniciar:** `docker compose restart backend`.
- **Cambiar una variable:** edita `/opt/nuvyra/.env` y ejecuta `docker compose up -d`.
