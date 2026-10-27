# ADR-001 — Monolito modular

**Estado:** aceptado · **Fecha:** 2026-10-27

## Contexto
La especificación pide arrancar como monolito modular, sin microservicios, Redis, Kafka ni Kubernetes
salvo necesidad demostrable. El equipo es pequeño y el dominio todavía evoluciona.

## Decisión
Un único servicio Spring Boot con paquetes por funcionalidad (`com.nuvyra.<módulo>`). Los módulos
se comunican solo por servicios públicos. Las tareas programadas corren dentro del mismo proceso.

## Consecuencias
* Despliegue, transacciones y pruebas simples (ACID real entre módulos).
* El rate limiting y las tareas programadas son **por instancia**. Si se escala horizontalmente habrá
  que añadir bloqueo distribuido para las tareas (p. ej. ShedLock sobre PostgreSQL) y un limitador
  compartido. Hasta entonces se ejecuta una sola instancia.
