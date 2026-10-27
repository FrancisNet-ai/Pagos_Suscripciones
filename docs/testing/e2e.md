# Recorridos E2E y CI

La suite de `frontend/e2e` prueba Angular contra la API real y PostgreSQL, sin sustituir respuestas.
Incluye compra sin registro, verificación manual, renovación sin duplicar cupos, contratación SaaS,
importación, invitación/revocación de colaboradores y resolución de pendientes con su historial.
Se ejecuta en Chromium de escritorio y móvil, con comprobaciones de accesibilidad y de ancho.

Validación local de este bloque: **173 pruebas backend**, **83 pruebas frontend**, compilación de
producción correcta y **10 recorridos E2E aprobados**. Las pruebas de historial cubren separación entre
empresas, permisos por sección, autor distinto del asignado, repetición tras rechazo, cancelación,
avisos enviados/omitidos, exclusión y rollback. No sustituyen una revisión previa al despliegue.

[Historial en escritorio](images/task-history-desktop.webp) · [Historial en móvil](images/task-history-mobile.webp).
Las capturas usan datos locales E2E identificados, sin ventas ni clientes reales.

## En GitHub Actions

El flujo `.github/workflows/ci.yml` tiene tres trabajos:

1. **Backend:** pruebas JUnit/Testcontainers y `bootJar`; publica temporalmente el JAR.
2. **Frontend:** instalación con lockfile, pruebas unitarias y compilación de producción.
3. **E2E:** espera a ambos, crea PostgreSQL 17, aplica Flyway al iniciar el JAR, crea el SUPER_ADMIN
   local de pruebas e inicia Angular con su proxy. Espera al health check y a la API a través de Angular
   antes de ejecutar `npm run e2e`.

El trabajo E2E fija Ubuntu 24.04 para mantener estables las dependencias de Chromium e instala solo
su variante headless. Usa las bibliotecas de Chrome incluidas en la imagen del runner, sin reinstalar
fuentes adicionales ni Xvfb para las pruebas headless. Playwright valida las bibliotecas al lanzar.
La instalación tiene un límite de cinco minutos; el trabajo completo, veinte.

Las credenciales declaradas en ese trabajo pertenecen exclusivamente al runner desechable. No se
usan secretos, servidores ni cuentas de producción. La configuración `dev` habilita cookies locales
para HTTP; no debe trasladarse a producción. Los pagos usan referencias ficticias identificadas como
E2E y se aprueban mediante las API normales; no se realizan transferencias ni envíos a Meta.

El artefacto `playwright-report` contiene el informe HTML, las capturas de los recorridos y los logs
de ambos servidores. Si hay un fallo, Playwright conserva también la traza. Retención: siete días.
El artefacto del JAR se conserva un día. Los artefactos pueden contener datos y enlaces de prueba:
deben tratarse como material de diagnóstico del repositorio.

## En local

Requisitos: JDK 21, Node 22 compatible con Angular 21, Docker/PostgreSQL 17 y dependencias instaladas.
Crear una base **desechable**, configurar `DB_URL`, `DB_USER` y `DB_PASSWORD`, y definir las siguientes
variables exclusivamente en la terminal de pruebas:

```bash
export SPRING_PROFILES_ACTIVE=dev
export APP_PUBLIC_BASE_URL=http://localhost:4200
export APP_BOOTSTRAP_SUPER_ADMIN_EMAIL=admin-e2e@example.test
export APP_BOOTSTRAP_SUPER_ADMIN_PASSWORD='Una-clave-exclusiva-de-pruebas'
export APP_RENEWALS_JOB_INTERVAL=PT1S
export APP_RENEWALS_JOB_INITIAL_DELAY=PT1S
```

Iniciar backend y Angular en terminales separadas:

```bash
cd backend
./gradlew bootRun
```

```bash
cd frontend
npm ci
npm start
```

Desde otra terminal, con `E2E_ADMIN_EMAIL` y `E2E_ADMIN_PASSWORD` iguales a los valores de bootstrap:

```bash
cd frontend
npx playwright install chromium
E2E_BASE_URL=http://localhost:4200 npm run e2e
npx playwright show-report
```

La suite rechaza destinos que no sean localhost o 127.0.0.1. Crea negocios y datos en cada ejecución;
para repetirla, usar una base vacía o reiniciar el entorno local y respetar los límites de solicitudes.
No desactivar esos límites en producción. El servidor debe conservar un plan E2E con al menos tres
usuarios para las pruebas de equipo; la suite lo crea automáticamente en la base nueva.
