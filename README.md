# Vigencia — Plataforma SaaS multiempresa de suscripciones digitales

Plataforma para que vendedores independientes administren la venta de suscripciones, productos y
servicios digitales: tienda pública por negocio, compras sin registro, cobros por Yape y Plin a las
cuentas del propio vendedor, verificación manual de pagos, clientes, cupos y renovaciones.

> «Vigencia» es un nombre provisional; se cambia en `frontend/src/app/core/brand.ts`.

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

Ver [plan de implementación](docs/plan-implementacion.md) y [pendientes](docs/BACKLOG.md).

## Tecnologías

| Capa | Stack |
|---|---|
| Frontend | Angular 21 (standalone, signals, zoneless), TypeScript estricto, Tailwind CSS 4, Angular CDK, Vitest |
| Backend | Kotlin 2.3, Spring Boot 4.1 (Web MVC, Security, Data JPA, Validation, Actuator), Flyway, springdoc-openapi |
| Datos | PostgreSQL 17 |
| Pruebas | JUnit 5, MockK, Spring Boot Test, Testcontainers |
| Infraestructura | Docker, Docker Compose, Nginx (proxy de mismo origen) |

## Estructura

```
backend/          API Kotlin/Spring Boot (monolito modular, paquete com.platform)
frontend/         SPA Angular (core / shared / features)
infrastructure/   Dockerfiles, configuración de Nginx y scripts de copias de seguridad
docs/             Arquitectura, modelo de datos, API, decisiones (ADR), despliegue
compose.yaml      Entorno completo con Docker
.env.example      Variables de entorno de referencia
```

## Desarrollo local

Requisitos: Java 21, Node 22 con npm 11, Docker.

```bash
# 1. Base de datos
docker run -d --name pg-dev -p 5432:5432 \
  -e POSTGRES_DB=platform -e POSTGRES_USER=platform -e POSTGRES_PASSWORD=platform postgres:17-alpine

# 2. Backend (perfil dev: http://localhost:8080, Swagger en /api/docs)
cd backend && ./gradlew bootRun

# 3. Frontend (http://localhost:4200, reenvía /api al backend)
cd frontend && npm install && npm start
```

En desarrollo, el enlace de recuperación de contraseña se escribe en el log del backend porque no hay SMTP.

### Pruebas

```bash
cd backend && ./gradlew test          # unitarias + integración (requiere Docker para Testcontainers)
cd frontend && npx ng test --watch=false
```

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
* [Decisiones técnicas (ADR)](docs/decisions)
