# Compila Angular y lo sirve con Nginx, que además actúa como proxy de mismo origen hacia /api.
# Contexto de build: raíz del repositorio.
FROM node:22-alpine AS build
WORKDIR /app
RUN npm install -g npm@11.21.1
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npx ng build --configuration production

FROM nginx:1.29-alpine
COPY infrastructure/docker/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY infrastructure/docker/nginx/security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=30s --timeout=3s CMD wget -qO- http://127.0.0.1/healthz || exit 1
