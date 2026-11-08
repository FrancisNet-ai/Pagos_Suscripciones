# Un solo servicio para Render (plan gratuito): compila Angular y el backend, y el backend sirve ambos en la misma
# dirección. Contexto de build: raíz del repositorio. Guía: docs/deployment/render-supabase.md
FROM node:24-alpine AS web
WORKDIR /web
RUN npm install -g npm@11.19.0
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npx ng build --configuration production

FROM eclipse-temurin:21-jdk AS api
WORKDIR /app
ENV GRADLE_OPTS="-Dorg.gradle.jvmargs=-Xmx1g"
COPY backend/gradlew backend/settings.gradle.kts backend/build.gradle.kts ./
COPY backend/gradle gradle
RUN ./gradlew dependencies --no-daemon -q > /dev/null
COPY backend/src src
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app --home /app app
WORKDIR /app
COPY --from=api --chown=app:app /app/build/libs/app.jar app.jar
COPY --from=web --chown=app:app /web/dist/frontend/browser static
USER app
ENV SPRING_PROFILES_ACTIVE=prod \
    APP_WEB_STATIC_DIR=/app/static \
    APP_STORAGE_TYPE=supabase \
    SERVER_TOMCAT_THREADS_MAX=20
EXPOSE 10000
# El plan gratuito tiene 512 MB: con estos límites el backend medido usó unos 440 MB bajo carga (montón de 192 MB,
# GC en serie, caché de código pequeña, sin el compilador C2 y pocos hilos de Tomcat).
ENTRYPOINT ["java", "-Xms48m", "-Xmx192m", "-XX:+UseSerialGC", "-Xss512k", "-XX:ReservedCodeCacheSize=40m", \
    "-XX:MaxMetaspaceSize=150m", "-XX:MaxDirectMemorySize=32m", "-XX:TieredStopAtLevel=1", "-Duser.timezone=UTC", \
    "-jar", "app.jar"]
