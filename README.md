# Nuvyra — Tu negocio, bajo control.

Plataforma SaaS multiempresa para la gestión de negocios digitales.

Plataforma para que vendedores independientes administren la venta de suscripciones, productos y
servicios digitales: tienda pública por negocio, compras sin registro, cobros por Yape y Plin a las
cuentas del propio vendedor, verificación manual de pagos, clientes, cupos y renovaciones.

> **Nuvyra** es el nombre comercial de trabajo hasta verificar su disponibilidad legal y de dominio.
> La marca vive en `frontend/src/app/core/brand.ts` y `backend/.../common/Brand.kt`; las tiendas públicas
> muestran siempre la marca de cada vendedor. Módulos internos: **Nuvyra Dashboard** (panel del vendedor),
> **Nuvyra Store** (tiendas públicas y checkout) y **Nuvyra Billing** (planes y cobros SaaS).
> Nombre provisional del monorepositorio: `nuvyra-platform`.

## Estado

**Fase 1 — Fundamentos: completada.** Registro e inicio de sesión de vendedores, creación del negocio
(slug y código empresarial), sesiones seguras, recuperación de contraseña, multiempresa resuelta en
servidor, auditoría, landing page, panel inicial e infraestructura Docker.

**Fase 2 — Negocios y productos: completada.** Configuración y personalización del negocio, cambio de
enlace público, Yape y Plin con titular declarado y QR, catálogo de productos con imágenes, publicación
y control de cupos sin sobreventa.

**Fase 3 — Tienda y checkout: completada.** Tienda pública por enlace, compra sin registro desde el
celular, pedidos `ORD-AAAA-NNNNNN`, instrucciones Yape/Plin con titular declarado, reporte de pago con
comprobante privado, verificación manual auditada, cupos reservados sin sobreventa y enlace a WhatsApp
con los datos reales del pedido.

**Fase 4 — Gestión comercial: completada.** Panel con ingresos confirmados y pendientes, clientes
(incluidos los que ya tenías antes de la plataforma), suscripciones con pago, activación y vencimiento
separados, cupos que se liberan solo al confirmar el retiro del acceso, y reportes por periodo, producto
y clientes recurrentes.

**Fase 5 — Renovaciones: completada.** Enlace personal de renovación con respuesta Sí/No, cupo
conservado al renovar, avisos antes y el día del vencimiento preparados para enviar por WhatsApp,
mensaje de despedida, exclusión de avisos, plazo de gracia configurable y métricas de renovación.

**Fase 6 — Comercialización del SaaS: completada.** Planes configurables por el superadministrador,
elección de plan en el registro, pago del plan por Yape/Plin verificado manualmente, plazo de gracia,
límites de productos y clientes, funciones por plan, panel de superadministración con ingresos SaaS
separados de las ventas, suspensión y reactivación de negocios y auditoría.

**Fase 7 — Integraciones avanzadas: completada.** WhatsApp Business Platform por negocio (token cifrado,
número verificado con Meta, plantillas aprobadas), envío automático de recordatorios solo a clientes que
lo autorizaron, webhooks firmados con estados de entrega y respuestas Sí/No por botón, importación de
clientes desde CSV o Excel con vista previa, y CI con GitHub Actions. Pagos automáticos: no aplican
mientras Yape y Plin no ofrezcan una API oficial.

**Colaboradores (`BUSINESS_STAFF`): completado.** El titular invita a su equipo con un enlace de un solo uso,
elige qué secciones ve cada colaborador (catálogo, pedidos y pagos, clientes, renovaciones, reportes) y
puede cambiar permisos o revocar el acceso al instante. La configuración, los cobros, el plan y el equipo
son siempre del titular. El número de usuarios respeta el límite del plan.

**Pendientes: completado.** Una bandeja con lo que los clientes esperan del vendedor (pagos por verificar,
servicios por activar, accesos por retirar, avisos por enviar), con responsable, antigüedad y plazo. Cada
pendiente se cierra solo cuando la acción se completa; nunca se marca como hecho a mano.
El historial conserva el autor real, la acción y su fecha, diferenciándolos del responsable asignado.
Los cierres anteriores o sin evidencia de autor se muestran como tales.

Ver [plan de implementación](docs/plan-implementacion.md) y [pendientes](docs/BACKLOG.md).

## Rediseño editorial

La interfaz sigue una dirección editorial azul con portada clara, arte fluido, paneles compactos y
flujos de compra móviles. Ver el [sistema visual](docs/architecture/sistema-visual.md) y las
[pruebas de extremo a extremo](docs/testing/e2e.md). Se conservan las funciones de las fases 1–7.

## Tecnologías

| Capa | Stack |
|---|---|
| Frontend | Angular 21 (standalone, signals, zoneless), TypeScript estricto, Tailwind CSS 4, Angular CDK, Vitest |
| Backend | Kotlin 2.4, Spring Boot 4.1 (Web MVC, Security, Data JPA, Validation, Actuator), Flyway, springdoc-openapi |
| Datos | PostgreSQL 17 |
| Pruebas | JUnit 5, MockK, Spring Boot Test, Testcontainers |
| Infraestructura | Docker, Docker Compose, Nginx (proxy de mismo origen) |

## Estructura

```
backend/          API Kotlin/Spring Boot (monolito modular, paquete com.nuvyra)
frontend/         SPA Angular (core / shared / features)
infrastructure/   Dockerfiles, configuración de Nginx y scripts de copias de seguridad
docs/             Arquitectura, modelo de datos, API, decisiones (ADR), despliegue
compose.yaml      Entorno completo con Docker
.env.example      Variables de entorno de referencia
```

## Desarrollo local

Requisitos recomendados: **Node 24.20**, **npm 11.19** y **JDK 21 LTS o JDK 26**. Gradle 9.4.1 usa el
JDK activo (`JAVA_HOME`) y genera código compatible con Java 21; desarrollar con Java 26 no exige
instalar Java 21. El contenedor de producción conserva Java 21 LTS.

Para ejecutar en Windows sin Docker ni virtualización, ver [la guía paso a paso](docs/development/windows.md).
La aplicación necesita PostgreSQL 17 local. Las pruebas backend con Testcontainers sí requieren Docker;
también se ejecutan automáticamente en GitHub Actions con Java 21 y Java 26.

```bash
# 1. Base de datos
docker run -d --name pg-dev -p 5432:5432 \
  -e POSTGRES_DB=nuvyra -e POSTGRES_USER=nuvyra -e POSTGRES_PASSWORD=nuvyra postgres:17-alpine

# 2. Backend (perfil dev: http://localhost:8080, Swagger en /api/docs)
cd backend && ./gradlew bootRun

# 3. Frontend (http://localhost:4200, reenvía /api al backend)
cd frontend && npm ci && npm start
```

En desarrollo, el enlace de recuperación de contraseña se escribe en el log del backend porque no hay SMTP.

### Pruebas

```bash
cd backend && ./gradlew test          # unitarias + integración (requiere Docker para Testcontainers)
cd frontend && npx ng test --watch=false
```

Playwright está versionado en `frontend/e2e` y se ejecuta en GitHub Actions después de las pruebas
de backend y frontend. El trabajo E2E usa PostgreSQL 17 desechable, el JAR compilado y Chromium
para escritorio y móvil; conserva informes, capturas, trazas de fallos y logs durante siete días.
Para ejecutarlo localmente, ver [la guía de pruebas E2E](docs/testing/e2e.md).

## Docker Compose

```bash
cp .env.example .env   # completar DB_PASSWORD y demás valores
docker compose up --build
```

Aplicación en http://localhost:8080. Detalles de producción, HTTPS, copias de seguridad y restauración
en [docs/deployment](docs/deployment/README.md).

## Documentación

* [Arquitectura técnica](docs/architecture/README.md)
* [Modelo de datos](docs/database/modelo-datos.md)
* [API REST v1](docs/api/api-v1.md)
* [Rutas del frontend](docs/architecture/rutas-frontend.md)
* [Sistema visual](docs/architecture/sistema-visual.md)
* [Seguridad y riesgos](docs/architecture/seguridad.md)
* [Dependencias y su revisión automática](docs/development/dependencias.md)
* [Textos legales (borradores para revisión)](docs/legal/README.md)
* [Decisiones técnicas (ADR)](docs/decisions)
