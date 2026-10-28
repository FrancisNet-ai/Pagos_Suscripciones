# ADR-006 — Desarrollo con Java 26 y Node 24

Estado: aceptado para compatibilidad con el equipo del propietario.

## Contexto

El equipo Windows del propietario tiene Node 24.20.0, npm 11.19.0 y JDK 26.0.2.1. Docker Desktop
no está disponible por la configuración de virtualización de Windows. El proyecto usaba Gradle 8.14.3
y exigía una toolchain Java 21, lo que impedía arrancar con solo el JDK 26 instalado.

## Decisión

- Gradle 9.4.1, cuyo soporte de ejecución incluye Java 26, con checksum oficial de la distribución.
- Kotlin 2.4.21: plugins, stdlib y reflect alineados; esta versión admite Gradle 9.4.1.
- Se usa el JDK activo (`JAVA_HOME`, o `java` en PATH) sin descargar o exigir otro JDK.
- Java y Kotlin generan bytecode 21. `--release 21` y `-Xjdk-release=21` limitan también las API del JDK
  usadas al compilar, manteniendo el mínimo Java 21 LTS del despliegue.
- Node 24.20 y npm 11.19 en desarrollo recomendado y CI. Angular 21 ya admite Node 24, por lo que
  no se realiza una migración mayor de Angular. El rango admitido conserva Node 22.12+ y 24.x.
- El build de Angular en Docker usa Node 24; el backend Docker conserva Java 21 LTS.
- CI compila y prueba en Java 21 y 26. E2E usa Node 24 y ejecuta sobre Java 26 el JAR compilado en Java 21.
- Un trabajo Windows comprueba además `gradlew.bat` y `bootJar` con Java 26, sin depender de Docker.
- PostgreSQL puede instalarse de forma nativa en Windows. Docker sigue siendo necesario únicamente
  para el entorno Compose y las pruebas de integración con Testcontainers, que también corren en CI.

No se modifica el dominio, la base de datos, los contratos REST ni la identidad visual. La compatibilidad
con Java 26 para desarrollo no cambia la elección de Java LTS para producción.

## Validación local

- 173 pruebas backend aprobadas en JDK 26.0.2.1 y la misma suite de 173 aprobada en JDK 21.
- 83 pruebas frontend y build de producción aprobados en Node 24.20.0 con npm 11.19.0.
- 10 recorridos Playwright aprobados contra el backend Java 26 y PostgreSQL 17, en escritorio/móvil.
- Arranque del JAR sobre Java 26, health de disponibilidad y generación OpenAPI correctos (94 rutas).
- Todas las clases propias del JAR mantienen versión de bytecode 65 (Java 21), y stdlib/reflect 2.4.21.
- SHA-256 del wrapper JAR y de la distribución comprobados contra los valores oficiales de Gradle.

## Referencias oficiales

- [Compatibilidad de Angular](https://angular.dev/reference/versions).
- [Gradle 9.4.1: soporte de Java 26](https://docs.gradle.org/9.4.1/release-notes.html).
- [Kotlin y versiones de Gradle](https://kotlinlang.org/docs/gradle-configure-project.html).
- [Requisitos de Spring Boot](https://docs.spring.io/spring-boot/system-requirements.html).
