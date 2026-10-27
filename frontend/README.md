# Frontend (Angular)

```bash
npm install        # npm 11 (npm 10 falla con un error de arborist al resolver jsdom)
npm start          # http://localhost:4200, proxy de /api a http://localhost:8080
npx ng test --watch=false
npx ng build       # producción en dist/frontend/browser
```

Estructura: `core/` (auth, guards, interceptors, servicios), `shared/` (componentes, validadores),
`features/` (una carpeta por funcionalidad, rutas con carga diferida). Tokens de diseño en
`src/styles/tokens.css`. Ver [docs/architecture](../docs/architecture).
