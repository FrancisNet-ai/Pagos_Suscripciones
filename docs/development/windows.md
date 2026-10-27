# Ejecutar Nuvyra en Windows sin Docker

Para la configuración del propietario: **Node 24.20.0, npm 11.19.0 y JDK 26.0.2.1**.
Se mantiene también soporte de compilación y ejecución con Java 21 LTS. El proyecto utiliza el JDK
activo sin obligar a instalar Java 21 cuando se dispone de Java 26.

## 1. Requisitos

- Instalar PostgreSQL 17 para Windows con pgAdmin. Guardar la contraseña elegida para `postgres`.
- Tener Node 24 y un JDK completo (21 o 26). Configurar `JAVA_HOME` hacia la carpeta del JDK, sin `bin`,
  y agregar su carpeta `bin` al PATH. Reiniciar Visual Studio Code después de instalar las herramientas.
- Abrir en VS Code la carpeta que contiene `backend`, `frontend` y `compose.yaml`.

Desde **Terminal → Nuevo terminal**, seleccionar **PowerShell** y comprobar:

```powershell
node --version
npm.cmd --version
java -version
javac -version
```

Estos comandos solo muestran versiones. No copiar como comandos el prefijo `C:\Users\…>` de la consola.
El símbolo `$env:` de los ejemplos siguientes corresponde a PowerShell, no a CMD.

## 2. Base de datos

En pgAdmin: conectar al servidor local, hacer clic derecho en **Databases → Create → Database**
y crear una base llamada `nuvyra`, con propietario `postgres`. Si ya existe, conservarla.
Las tablas y migraciones se crean automáticamente al iniciar el backend; no importar SQL a mano.

## 3. Backend

Desde una terminal PowerShell ubicada en la raíz del proyecto:

```powershell
cd backend
$env:SPRING_PROFILES_ACTIVE="dev"
$env:DB_URL="jdbc:postgresql://localhost:5432/nuvyra"
$env:DB_USER="postgres"
$env:DB_PASSWORD="TU_CLAVE_DE_POSTGRESQL"
$env:APP_PUBLIC_BASE_URL="http://localhost:4200"
$env:APP_BOOTSTRAP_SUPER_ADMIN_EMAIL="admin@nuvyra.test"
$env:APP_BOOTSTRAP_SUPER_ADMIN_PASSWORD="NuvyraLocal-2026!"
.\gradlew.bat --version
.\gradlew.bat bootRun
```

Reemplazar `TU_CLAVE_DE_POSTGRESQL` por la contraseña que se eligió al instalar PostgreSQL.
El primer comando Gradle debe identificar el JDK activo; descarga Gradle, Kotlin y las dependencias
durante la primera ejecución. No hace falta instalar Gradle por separado ni instalar otro JDK.
Esperar el mensaje `Started NuvyraApplicationKt` y dejar esa terminal abierta.

Las variables de `$env:` se aplican solo a esa terminal. Un archivo `.env` de Docker no se carga
automáticamente en esta modalidad. Usar el perfil `dev` y estas credenciales solo en el equipo local.
La configuración de desarrollo proporciona claves locales para poder arrancar sin credenciales externas.

## 4. Frontend

Abrir otra terminal PowerShell, ubicada de nuevo en la raíz del proyecto:

```powershell
cd frontend
npm.cmd ci
npm.cmd start
```

Abrir **http://localhost:4200**. Angular reenvía `/api` al backend en el puerto 8080. Mantener ambos
procesos abiertos; detener cada uno con `Ctrl+C`. Los cambios de Angular se actualizan automáticamente.
Para cambios de Kotlin, reiniciar `bootRun`.

El administrador local es `admin@nuvyra.test` / `NuvyraLocal-2026!`. En `/admin` se configuran los planes
y métodos de cobro SaaS antes de dar de alta vendedores. Las cuentas de compradores no se crean.
No hay productos ni ventas de demostración insertados automáticamente.

## 5. Comprobaciones

```powershell
# Desde frontend
npm.cmd test -- --watch=false
npm.cmd run build
```

Las pruebas de integración de backend (`.\gradlew.bat test`) utilizan Testcontainers y necesitan Docker.
La aplicación con PostgreSQL nativo y el comando `bootRun` funcionan sin Docker ni virtualización.
GitHub Actions ejecuta las pruebas de backend en Java 21 y 26, y los recorridos E2E en escritorio y móvil.

Si algo falla, compartir el mensaje de error, sin contraseñas ni tokens. Un error de conexión a PostgreSQL
se revisa verificando servicio, puerto, nombre de base, usuario y contraseña. `JAVA_HOME` debe apuntar
a un JDK real; si apunta a otra versión, Gradle lo muestra en `--version`.
