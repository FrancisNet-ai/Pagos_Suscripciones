# Producción gratuita — plan y avance

Guía para publicar Nuvyra sin costo y que no dependa de la laptop del titular. Se puede seguir desde
cualquier dispositivo: marca cada casilla (`[x]`) al completarla.

Última actualización: 28 de octubre de 2026.

## Por qué así

- **La laptop no sirve como servidor:** se apaga, no es accesible desde internet, no tiene copias de
  seguridad y no tiene virtualización (no puede usar Docker). Queda solo para desarrollo, con
  PostgreSQL 17 nativo, JDK 26 y Node 24 (ver [desarrollo en Windows](../development/windows.md)).
- **Presupuesto: 0.** Solo se usan servicios gratuitos.
- **Lo que necesita Nuvyra en producción:**
  - Un backend **encendido siempre**, porque hay tareas programadas: recordatorios, vencimientos y pendientes.
  - Un **disco permanente** para logos, productos y comprobantes.
  - Una **sola instancia** (aún no hay bloqueo distribuido).
- **Descartados:**
  - Render gratis: se duerme y pierde los archivos.
  - Vercel: no ejecuta un backend Java siempre encendido.
  - Supabase gratis: es solo la base de datos.
  - La máquina gratuita de Google Cloud: tiene 1 GB de RAM, insuficiente.
- **Elegido: Oracle Cloud «Always Free».** Ofrece un servidor ARM gratuito y permanente, con hasta 4
  núcleos, 24 GB de RAM y 200 GB de disco según su oferta actual. Ahí corre el `compose.yaml` del
  repositorio: PostgreSQL, backend y web.
- **Más adelante:** cuando haya vendedores pagando su plan, se puede pasar a un VPS de pago (unos 5 USD
  al mes) y a un dominio propio con el mismo `compose.yaml`.

## Montaje a costo 0

| Pieza | Servicio gratuito |
|---|---|
| Servidor (base, backend y web) | Oracle Cloud Always Free |
| Dirección web con HTTPS | Subdominio de DuckDNS + Caddy (certificado automático) |
| Copias de seguridad diarias | Object Storage de Oracle (incluido en la misma cuenta) |
| Alerta si se cae | UptimeRobot, plan Free |
| Despliegue automático | GitHub Actions |

Docker solo se usa en el servidor de Oracle y en GitHub Actions, nunca en la laptop.

## Paso 1 — Cuentas que crea el titular

### 0. Correo del proyecto (recomendado)
- [ ] Crear un Gmail solo para Nuvyra, por ejemplo `nuvyra.soporte@gmail.com`, y usarlo en todas las
  cuentas. Servirá también para enviar los correos de recuperación de contraseña.

### 1. Oracle Cloud (el servidor) — la más importante
- [ ] Registrarse en https://www.oracle.com/cloud/free/ con el botón «Start for free».
  - Se necesita correo, número de celular y una tarjeta de débito o crédito, solo para verificar la
    identidad. Puede aparecer un cargo temporal pequeño que se devuelve.
  - Tipo de cuenta: «Individual».
  - **Home Region: no se puede cambiar.** Elegir la más cercana a Perú: Bogotá, Santiago o São Paulo.
  - Comprobar que la cuenta queda en «Always Free» o «Free Tier». **No activar mejoras de pago.**
  - La verificación puede tardar desde minutos hasta un día.

### 2. DuckDNS (dirección web gratis)
- [ ] Entrar en https://www.duckdns.org con GitHub o Google.
- [ ] Reservar un subdominio, por ejemplo `nuvyra.duckdns.org`. Si está ocupado, probar `nuvyra-pe` o
  `minuvyra`. La IP se configura después, cuando exista el servidor.

### 3. UptimeRobot (alerta si se cae)
- [ ] Crear la cuenta Free en https://uptimerobot.com. El monitor se configura al final, cuando la app
  esté publicada.

### Ya disponible
- GitHub: organización `FrancisNet-ai`, para el código y el despliegue automático.

## Seguridad

- **Contraseñas y tokens:** no se escriben en el chat, en issues ni en el repositorio. Esto incluye el
  token de DuckDNS, las claves de Oracle y la contraseña de la base.
- **Dónde se guardan:** como **secretos de GitHub** (Settings → Secrets and variables → Actions) o en
  el archivo `.env` del servidor, que nunca se sube.
- **Qué se puede compartir para continuar:**
  - La **región** de Oracle.
  - El **subdominio** de DuckDNS.
  - Más adelante, la **IP pública** del servidor.

Avance del paso 1, para completar al terminar:
- Región de Oracle: _pendiente_
- Subdominio de DuckDNS: _pendiente_

## Paso 2 — Archivos del repositorio (los prepara el asistente; no requieren cuentas)

- [x] Caddy con HTTPS automático para el subdominio (`compose.prod.yaml` e `infrastructure/caddy/Caddyfile`).
- [x] Copia de seguridad diaria, cifrada y enviada a Object Storage (`infrastructure/scripts/backup-offsite.sh`).
- [x] Workflow de GitHub Actions que despliega en el servidor cuando el CI pasa en `main`
  (`.github/workflows/despliegue.yml`). Queda inactivo hasta definir `DEPLOY_ENABLED=true`.
- [x] Guía de creación del servidor en Oracle: VM ARM Ubuntu, puertos 80 y 443, Docker y SSH
  ([oracle-servidor.md](oracle-servidor.md)).

## Paso 3 — Poner en marcha (con las cuentas listas)

Cada casilla está explicada paso a paso en [oracle-servidor.md](oracle-servidor.md).

- [ ] Crear la VM ARM «Always Free» en Oracle y abrir los puertos 80 y 443.
- [ ] Apuntar el subdominio de DuckDNS a la IP pública del servidor.
- [ ] Instalar Docker en el servidor y crear el `.env` de producción, con secretos generados en el
  servidor.
- [ ] Guardar en GitHub los secretos del despliegue (acceso SSH). El token de DuckDNS queda solo en el
  `.env` del servidor.
- [ ] Primer despliegue y comprobación de `https://<subdominio>.duckdns.org/actuator/health`.
- [ ] Crear el superadministrador (`APP_BOOTSTRAP_SUPER_ADMIN_*`) y, desde `/admin`, las cuentas
  Yape/Plin de la plataforma y los planes.
- [ ] Configurar UptimeRobot sobre `/actuator/health`.
- [ ] Probar una restauración de la copia de seguridad.

## Limitaciones conocidas del plan gratuito

- **Disponibilidad de servidores:** a veces no hay servidores ARM libres en la región; hay que reintentar.
- **Máquinas sin uso:** Oracle puede recuperar las máquinas gratuitas que pasan mucho tiempo sin uso.
- **Sin garantía:** no hay garantía de disponibilidad (SLA).
- **Subdominio:** el subdominio gratuito se ve menos profesional; se puede cambiar a un dominio propio
  sin tocar el código.

## Estado relacionado del proyecto

- **PR #14** (portada centrada en inicio, soluciones y tarifas): pendiente de revisión y fusión.
- **Base local `nuvyra`:** existe en la laptop, pero sin superadministrador ni planes. Por eso la
  portada muestra «Los planes se publicarán aquí».
