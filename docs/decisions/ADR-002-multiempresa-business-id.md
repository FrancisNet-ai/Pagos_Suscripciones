# ADR-002 — Multiempresa con `business_id` y resolución en servidor

**Estado:** aceptado · **Fecha:** 2026-10-27

## Contexto
Varios vendedores comparten la aplicación y la base de datos. Ninguno debe acceder a datos de otro.

## Decisión
* Esquema compartido con columna `business_id` (UUID) en cada tabla de negocio.
* El negocio del usuario se resuelve en el servidor a partir de la sesión y `business_members`
  (`CurrentBusinessResolver`), nunca de parámetros enviados por el cliente.
* Repositorios con métodos acotados por `business_id`; recursos ajenos devuelven 404.
* Pruebas de integración de aislamiento obligatorias por módulo.

## Alternativas descartadas
* Esquema o base por empresa: más aislamiento, pero migraciones y operación mucho más costosas para un MVP.
* Row Level Security de PostgreSQL: valiosa como defensa en profundidad; se evaluará más adelante
  (requiere fijar `app.business_id` por transacción y cuidar el pool de conexiones).
