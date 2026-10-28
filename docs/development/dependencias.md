# Dependencias del proyecto

Inventario único de lo que usa Nuvyra, para qué sirve cada pieza y dónde se cambia su versión.
Las versiones exactas no se copian aquí para que el documento no quede desactualizado: la fuente
de verdad es el archivo indicado en cada sección.

## Dónde vive cada versión

| Parte | Archivo único de versiones | Bloqueo exacto |
|---|---|---|
| Backend (Kotlin / Spring Boot) | `backend/gradle/libs.versions.toml` | BOM de Spring Boot |
| Frontend (Angular) | `frontend/package.json` | `frontend/package-lock.json` |
| Herramientas de ejecución | `.github/workflows/ci.yml` (Java, Node, npm) | — |
| Imágenes de contenedor | `infrastructure/docker/*.Dockerfile` | — |

`backend/build.gradle.kts` no declara versiones: usa los alias del catálogo (`libs.…`). Las
bibliotecas del catálogo sin `version` las fija el BOM de Spring Boot, así todas quedan
compatibles entre sí y se actualizan juntas al subir Spring Boot.

## Backend

| Grupo | Dependencias | Para qué |
|---|---|---|
| Base | Kotlin (JVM, `spring`, `jpa`), Spring Boot, Spring Dependency Management | Lenguaje, arranque de la aplicación y versiones alineadas |
| Web y seguridad | `starter-webmvc`, `starter-security`, `starter-validation`, `starter-actuator`, `starter-mail` | API REST, sesiones con CSRF, validación de entrada, salud del servicio y correos |
| Base de datos | `starter-data-jpa`, `starter-flyway`, `flyway-database-postgresql`, `postgresql` | Acceso a PostgreSQL y migraciones versionadas (`db/migration`) |
| Kotlin y JSON | `kotlin-reflect`, `jackson-module-kotlin` (Jackson 3, `tools.jackson`) | Serialización de las clases de datos |
| Documentación de la API | `springdoc-openapi-starter-webmvc-ui` | Swagger en desarrollo; desactivado en producción |
| Importación | `poi-ooxml` (Apache POI) | Leer clientes desde Excel `.xlsx` |
| Pruebas | `starter-webmvc-test`, `starter-security-test`, Testcontainers (PostgreSQL), `kotlin-test-junit5`, MockK, SpringMockK | Pruebas de integración contra una base real y dobles de prueba |

## Frontend

| Grupo | Dependencias | Para qué |
|---|---|---|
| Aplicación | `@angular/core`, `common`, `compiler`, `forms`, `platform-browser`, `router`, `cdk`, `rxjs`, `tslib` | Interfaz, formularios, rutas y accesibilidad (CDK) |
| Tipografías | `@fontsource-variable/inter`, `playfair-display`, `bodoni-moda` | Fuentes servidas desde la propia app, sin terceros |
| Compilación | `@angular/build`, `@angular/cli`, `@angular/compiler-cli`, `typescript` | Compilar y servir la aplicación |
| Estilos | `tailwindcss`, `@tailwindcss/postcss`, `postcss` | Sistema visual |
| Pruebas | `vitest`, `jsdom`, `@playwright/test`, `@axe-core/playwright` | Pruebas unitarias, de extremo a extremo y de accesibilidad |
| Formato | `prettier` | Formato uniforme del código |

Solo el grupo «Aplicación» y las tipografías llegan al navegador; el resto son herramientas de
desarrollo (`devDependencies`).

## Herramientas y contenedores

- **Java:** 21 o 26 (Temurin). El CI prueba ambas y Windows con 26.
- **Node 24 y npm 11:** fijados en `package.json` (`engines`, `packageManager`) y en el CI.
- **PostgreSQL 17:** en el CI E2E y en `infrastructure/docker`.
- **Imágenes:** `eclipse-temurin` para el backend, y `node` más `nginx` para la web.

## Revisión automática

- **Dependabot** (`.github/dependabot.yml`):
  - Abre los lunes PR agrupados por ecosistema: Gradle, npm, GitHub Actions y Docker.
  - Las actualizaciones de seguridad llegan apenas se publican.
  - Cada PR pasa por el CI completo antes de fusionarse.
- **Seguridad de dependencias** (`.github/workflows/seguridad-dependencias.yml`):
  - `npm audit` falla si hay vulnerabilidades altas o críticas en lo que llega al navegador.
  - Corre en cada PR que cambia dependencias y cada lunes.
  - En `main` envía el grafo de Gradle a GitHub para que Dependabot revise también las
    dependencias transitivas del backend.
- **Activación única en GitHub:** en *Settings → Code security*, activa
  *Dependency graph*, *Dependabot alerts* y *Dependabot security updates*.

## Cómo actualizar a mano

**Backend:**
1. Cambia la versión en `backend/gradle/libs.versions.toml`.
2. Para subir Spring Boot, cambia solo `spring-boot`: el BOM arrastra al resto.
3. Ejecuta `./gradlew test`.

**Frontend:**
1. Ejecuta `npm install paquete@versión` en `frontend/`; actualiza `package.json` y el lockfile.
2. Para Angular, usa `npx ng update @angular/core @angular/cli`.
3. Ejecuta `npx ng test --watch=false`, `npx ng build` y `npm audit --omit=dev`.

**Para agregar una dependencia nueva:**
- Registra primero su alias en el catálogo (backend) o en el grupo correspondiente.
- Agrega una línea en la tabla de este documento explicando para qué se usa.
