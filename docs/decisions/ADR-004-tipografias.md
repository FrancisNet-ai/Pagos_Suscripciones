# ADR-004 — Tipografías con licencia libre temporal

**Estado:** aceptado · **Fecha:** 2026-10-27

## Contexto
La identidad pide Voga (display), Bodoni FLF (editorial) e Inter (interfaz). No se dispone de archivos
de Voga ni de Bodoni FLF con licencia verificada para distribución web en un SaaS comercial.

## Decisión
Usar **Playfair Display** en lugar de Voga y **Bodoni Moda** en lugar de Bodoni FLF (ambas OFL),
e **Inter** (OFL). Se autoalojan con paquetes `@fontsource-variable`. Las familias se referencian solo
mediante los tokens `--font-display`, `--font-editorial` y `--font-ui`.

## Consecuencias
Cambiar a Voga/Bodoni FLF requiere únicamente adquirir la licencia, añadir los archivos (fuera de
repositorios públicos) y modificar dos tokens.
