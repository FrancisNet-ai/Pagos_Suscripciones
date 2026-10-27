# ADR-003 — Sesiones opacas en cookie HttpOnly

**Estado:** aceptado · **Fecha:** 2026-10-27

## Contexto
La especificación prefiere cookies `HttpOnly; Secure; SameSite` y prohíbe guardar credenciales en
`localStorage`. La SPA y la API se sirven desde el mismo origen.

## Decisión
* Token aleatorio de 256 bits en cookie `HttpOnly`. En la base se guarda solo su SHA-256 (`user_sessions`).
* Expiración por inactividad y absoluta; rotación con `POST /auth/refresh`; revocación en logout y al
  cambiar la contraseña.
* CSRF con *double submit cookie* de Spring Security, compatible con `HttpClient` de Angular.

## Alternativas descartadas
* JWT en `localStorage`: expuesto a XSS.
* JWT en cookie: revocación compleja; no aporta nada en un monolito con base de datos.
* Spring Session JDBC: válido, pero añade tablas y comportamiento implícito; la tabla propia es más
  explícita y permite listar y revocar sesiones por usuario.

## Consecuencias
Una consulta indexada por petición autenticada (aceptable). La cookie se llama `__Host-sid` en
producción (exige `Secure`, sin `Domain`) y `sid` en desarrollo por HTTP.
