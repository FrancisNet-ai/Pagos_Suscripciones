# Producción gratuita en Render + Supabase

Plan vigente para publicar Nuvyra sin costo y sin depender de la laptop del titular. Reemplaza al de Oracle
Cloud, que rechazó el registro (ver [ADR-008](../decisions/ADR-008-render-supabase.md)).

| Pieza | Servicio gratuito | Qué hace |
|---|---|---|
| Backend y página web | **Render**, servicio web Free (Oregón) | Un solo servicio: la API y Angular en la misma dirección `https://<servicio>.onrender.com` |
| Base de datos | **Supabase**, proyecto `Nuvyra` (`us-west-2`, Oregón) | PostgreSQL 17 con las mismas migraciones y la protección RLS |
| Imágenes y comprobantes | **Supabase Storage** | Dos buckets privados que el backend crea solo (`nuvyra-media` y `nuvyra-private`) |
| Mantenerlo despierto | **UptimeRobot** | Visita `/actuator/health` cada 5 minutos: Render duerme el servicio tras 15 minutos sin tráfico |
| Copias de seguridad | **GitHub Actions** | Copia diaria de la base, cifrada con AES-256 (`copia-supabase.yml`) |
| Correos del sistema | **Brevo**, plan gratuito (300 correos al día) | Recuperación de contraseña e invitaciones al equipo, por su API HTTPS |

**Nunca pegues en el chat, en issues ni en el repositorio** contraseñas, claves de Supabase ni la frase de
cifrado. Van solo en Bitwarden, en Render y en los secretos de GitHub.

## Límites conocidos del plan gratuito
- **Velocidad:** Render Free tiene 512 MB de memoria y poca CPU. El backend está ajustado (unos 440 MB medidos
  bajo carga). La primera visita tras un reinicio puede tardar alrededor de un minuto.
- **Horas:** Render da 750 horas al mes. Un mes tiene unas 730, así que alcanza para tener **un** servicio
  encendido siempre. No crees otros servicios Free en la misma cuenta.
- **Correo:** Render Free **bloquea el correo saliente por SMTP** (puertos 25, 465 y 587). Por eso los correos
  salen por la API HTTPS de Brevo (sección 7).
- **Supabase Free:** 500 MB de base de datos y 1 GB de archivos. Pausa el proyecto si pasa una semana sin
  actividad; con el backend conectándose cada pocos minutos no debería ocurrir.
- **Disponibilidad:** sin garantía (SLA). Cuando haya ingresos, se pasa a planes de pago sin cambiar el código.

## 1. Datos de Supabase (en Bitwarden, nunca en el chat)
En el panel de Supabase, proyecto **Nuvyra**:
1. **Connect → Session pooler** (no uses «Direct connection» ni «Transaction pooler»). Anota:
   - **host**, por ejemplo `aws-0-us-west-2.pooler.supabase.com`;
   - **usuario**: `postgres.nipxkgobbourlebobyrx`;
   - **puerto**: `5432`.
2. **Project Settings → API Keys → Secret keys**: crea o revela una clave secreta (`sb_secret_…`) y guárdala en
   Bitwarden. Da acceso total al proyecto: solo va en Render.
3. La **contraseña de la base de datos** ya está en Bitwarden desde que creaste el proyecto.

El modo **sesión** del pooler es obligatorio: la protección RLS fija el negocio en cada conexión, y el modo
transacción la reutilizaría entre peticiones.

## 2. Crear el servicio en Render
1. En https://dashboard.render.com: **New → Blueprint**. Elige el repositorio `FrancisNet-ai/Pagos_Suscripciones`.
   Render lee `render.yaml` y propone el servicio **nuvyra** (Docker, Free, Oregón).
2. Render pide los valores marcados como secretos. Llénalos así:

| Variable | Valor |
|---|---|
| `DB_URL` | `jdbc:postgresql://<host del Session pooler>:5432/postgres?sslmode=require` |
| `DB_USER` | `postgres.nipxkgobbourlebobyrx` |
| `DB_PASSWORD` | La contraseña de la base (Bitwarden) |
| `APP_PUBLIC_BASE_URL` | `https://nuvyra.onrender.com`. Si Render asigna otro nombre, corrígelo después del primer despliegue |
| `APP_STORAGE_SUPABASE_URL` | `https://nipxkgobbourlebobyrx.supabase.co` |
| `APP_STORAGE_SUPABASE_SECRET_KEY` | La clave `sb_secret_…` (Bitwarden) |
| `APP_BOOTSTRAP_SUPER_ADMIN_EMAIL` | El correo del proyecto Nuvyra |
| `APP_BOOTSTRAP_SUPER_ADMIN_PASSWORD` | Una contraseña nueva de 12 caracteres o más, generada en Bitwarden |
| `APP_MAIL_FROM` | El correo del proyecto Nuvyra (debe ser el remitente verificado en Brevo) |
| `APP_MAIL_BREVO_API_KEY` | La clave de API de Brevo (sección 7). Si la dejas vacía, no sale ningún correo |

   `APP_RENEWAL_TOKEN_SECRET` y `APP_SECRETS_ENCRYPTION_KEY` los genera Render solo. No los cambies después:
   invalidarían los enlaces de renovación y los secretos cifrados.
3. **Apply / Create**. El primer despliegue compila la página y el backend y tarda varios minutos. Al terminar,
   `https://<servicio>.onrender.com/actuator/health` debe responder `{"status":"UP"}`.
4. Si al arrancar el registro muestra «Configuración insegura para producción», el mensaje dice qué variable
   corregir (`ProductionSafetyCheck`).

Desde entonces, cada fusión en `main` con el CI en verde se publica sola (`autoDeployTrigger: checksPass`).

## 3. Primer ingreso
1. Entra a `https://<servicio>.onrender.com/ingresar` con el superadministrador.
2. En **Mi cuenta**: cambia la contraseña y activa la **verificación en dos pasos** (obligatoria para usar
   `/admin`). Guarda los códigos de recuperación en Bitwarden.
3. En `/admin`: crea los planes y las cuentas Yape/Plin de la plataforma.
4. En Render, vacía `APP_BOOTSTRAP_SUPER_ADMIN_EMAIL` y `APP_BOOTSTRAP_SUPER_ADMIN_PASSWORD`: ya no se necesitan.

## 4. Mantenerlo despierto (UptimeRobot)
En el monitor que ya existe:
1. Edítalo para que revise `https://<servicio>.onrender.com/actuator/health` cada **5 minutos**.
2. Pulsa **Resume** para reanudarlo.

Además de mantenerlo despierto, te avisa por correo si la página se cae.

## 5. Copias de seguridad diarias
En GitHub, *Settings → Secrets and variables → Actions*:

| Tipo | Nombre | Valor |
|---|---|---|
| Secret | `SUPABASE_DB_URL` | `postgresql://postgres.nipxkgobbourlebobyrx:<contraseña>@<host del Session pooler>:5432/postgres?sslmode=require` |
| Secret | `BACKUP_ENCRYPTION_PASSPHRASE` | Una frase larga generada en Bitwarden. **Sin ella no se pueden recuperar las copias** |
| Variable | `SUPABASE_BACKUP_ENABLED` | `true` |

Luego, en *Actions → Copia de seguridad (Supabase) → Run workflow*, ejecútala una vez para comprobarla. Cada
copia queda como artefacto del workflow durante 30 días. El repositorio es público, así que cualquier usuario de
GitHub puede descargar el archivo; por eso va cifrado y la frase vive solo en Bitwarden y en GitHub.

Las imágenes y los comprobantes quedan en Supabase Storage y no entran en esta copia.

**Restaurar** (en un PostgreSQL 17 vacío):

```bash
openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -pass env:BACKUP_ENCRYPTION_PASSPHRASE -in nuvyra-AAAAmmdd-HHMMSS.dump.enc -out nuvyra.dump
pg_restore --no-owner --no-privileges -d "<cadena de conexión>" nuvyra.dump
```

## 6. Dirección propia (opcional)
`nuvyra.duckdns.org` no puede apuntar a Render, porque DuckDNS solo admite direcciones IP y Render pide un
registro CNAME. Hasta tener un dominio propio, la dirección es la de Render. Un dominio propio (por ejemplo
`.pe` o `.com`) se agrega en Render → Settings → Custom Domains, sin tocar el código.

## 7. Correos del sistema (Brevo)
Sin esto no salen los correos de recuperación de contraseña ni las invitaciones al equipo (el titular puede
seguir copiando el enlace de invitación que muestra el panel).

1. Crea una cuenta gratuita en https://www.brevo.com con el correo del proyecto Nuvyra. Ese correo queda como
   remitente verificado. Si usas otro remitente, agrégalo y verifícalo en **Senders, Domains & Dedicated IPs → Senders**.
2. En **SMTP & API → API Keys** (`https://app.brevo.com/settings/keys/api`), genera una clave nueva:
   - Nombre `nuvyra-render`, vencimiento **Sin vencimiento**.
   - Acceso **Restringido**, con **Emails transaccionales: Todos** y todo lo demás en **Ninguno**.
   - Guárdala en Bitwarden. Empieza por `xkeysib-` y solo se muestra una vez.
   - Brevo desactiva una clave **tras 90 días sin uso**, aunque no venza. Si pasan tres meses sin correos,
     genera otra y reemplázala en Render.
3. En Render → **Environment**: pega la clave en `APP_MAIL_BREVO_API_KEY` y revisa que `APP_MAIL_FROM` sea el
   remitente verificado. **Save and deploy**.
4. Prueba: en `/recuperar` pide un enlace para tu propio correo. Debe llegar en un minuto (revisa también Spam).

Si no llega:
- En el registro de Render busca «No se pudo enviar el correo de recuperación». El mensaje dice qué respondió Brevo.
- `401` con «Key not found» o clave desactivada: la clave venció o pasó 90 días sin uso. Genera otra.
- `401` con «unrecognised IP address»: en Brevo, **Security → Authorized IPs**, desactiva el bloqueo por IP
  (Render no tiene una IP fija en el plan gratuito).
- Un remitente de correo gratuito (Hotmail, Gmail) llega peor que uno de dominio propio. Con un dominio propio,
  verifícalo en Brevo (registros DKIM y DMARC) y cambia `APP_MAIL_FROM`.
