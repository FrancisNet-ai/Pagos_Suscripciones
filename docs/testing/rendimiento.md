# Conexiones lentas e inestables

## Cambios

- CSRF y sesión se consultan en paralelo. El arranque sigue esperando ambas respuestas antes de evaluar los permisos.
- El origen Spring Boot comprime HTML, CSS, JavaScript, JSON y SVG a partir de 1 KiB. Las imágenes y las fuentes, que ya están comprimidas, conservan su formato.
- El arte y los iconos sin hash se reutilizan durante una hora, con revalidación en segundo plano. Los archivos de Angular con hash conservan su caché de un año. `index.html` se revalida; los datos de sesión, pedidos y pagos conservan las cabeceras privadas de Spring Security.
- Al servir Angular desde el backend, `/media/**` apunta a la carpeta `media/` del build. Se comprueba que las fuentes se descarguen correctamente, además de sus cabeceras de caché.
- En móviles de hasta 768 px la portada de la tienda solicita la versión reducida de 640 px. Si es una imagen antigua o WebP sin versión reducida, el servidor conserva el original. Los QR mantienen su resolución.
- Se retira Playfair Display: ninguna pantalla la utilizaba. La identidad conserva Bodoni Moda e Inter, con sus archivos y licencias de Fontsource.
- Las lecturas JSON de `/api/v1/` tienen un límite de 30 segundos por intento y un único reintento, tras 600 ms, ante cortes o errores 502/503/504. Sin conexión declarada por el navegador se devuelve el error sin reintentar. Los errores de autorización, validación y límites de solicitudes no se repiten.
- Pedidos, pagos y cualquier otra escritura se envían una sola vez por acción. Los comprobantes descargados como binarios quedan fuera del límite de lecturas JSON. Un reintento manual del checkout conserva los campos y la clave de idempotencia mientras la página permanezca abierta.
- El aviso de desconexión informa del estado del navegador. No se guardan pedidos ni datos personales en una caché offline, ni se envían operaciones al reconectar.

## Medición reproducible del frontend

La herramienta `frontend/tools/measure-network.mjs` sirve un build de producción en **localhost** y abre Chromium con:

- viewport 390 × 844;
- descarga 64 KiB/s, subida 32 KiB/s y latencia configurada de 300 ms;
- CPU ralentizada 4 veces y caché vacía en cada muestra;
- API simulada de visitante, con 500 ms de respuesta para autenticación y planes vacíos;
- tres muestras por variante, usando la mediana.

No consulta producción ni mide PostgreSQL, las transferencias bancarias o el arranque en frío de Render.
El tiempo de contenido corresponde a la aparición del titular real de la portada, no al monograma de espera.
Los bytes incluyen los recursos descargados durante la visita y las cabeceras contabilizadas por Chromium.

```bash
cd frontend
npm ci
npx ng build
npx playwright install --only-shell chromium
node tools/measure-network.mjs dist/frontend/browser --gzip
```

Para comparar revisiones, conservar el directorio `dist/frontend/browser` de cada build y pasarlo como primer argumento.
Sin `--gzip` se mide un origen sin compresión; con él se aplica gzip a los tipos de texto. El script usa una API de muestra deliberadamente limitada y no acepta escrituras.

Resultados locales del 9 de noviembre de 2026, base `61eb8e5`:

| Variante | Contenido principal | Transferencia total |
|---|---:|---:|
| Base, origen sin compresión | 9.501 ms | 590.549 bytes |
| Base con gzip, como control | 5.577 ms | 331.370 bytes |
| Cambios con gzip | 5.186 ms | 332.567 bytes |

En este escenario, habilitar compresión junto con los cambios reduce el tráfico un **43,7 %** y el tiempo hasta el contenido un **45,4 %**. Si el proxy ya comprimía, la comparación relevante es el control: **7,0 %** menos tiempo, con unos 1,2 kB adicionales por la recuperación de lecturas y el aviso de conexión. Son resultados de laboratorio, no una garantía para toda conexión.

El build pasa de 15 archivos de fuentes (383.964 bytes) a 11 (294.156 bytes), un 23,4 % menos en los archivos de tipografía distribuidos. No se atribuye ese ahorro a cada visita: los navegadores solo descargan las fuentes que utilizan.

## Verificación

Validación local del cambio: **227 pruebas backend**, **127 pruebas frontend**, **22 recorridos Playwright**
en escritorio/móvil, build de producción y `nginx -t` aprobados. También se abrió el build servido por el JAR:
JavaScript con gzip y fuentes y arte con respuestas 200 y sus cabeceras de caché correspondientes.

- Pruebas de integración del servidor real: gzip negociado, respuesta legible sin gzip, rutas de fuentes y arte, caché pública y sesión con `no-store`.
- Pruebas unitarias: arranque concurrente, límites de reintento, errores que no se repiten, cancelación de lecturas colgadas y descarga binaria sin ese límite.
- `connectivity.spec.ts`: corte transitorio de tienda, pérdida y recuperación de conexión con formulario conservado, pedido enviado solo por acción manual con la misma clave y portada móvil sin descargar la versión original.
- Los recorridos comerciales existentes siguen probando compras y renovaciones contra la API y PostgreSQL reales. Las pruebas de cortes utilizan respuestas controladas y están identificadas como tales.

El plan gratuito de Render y un posible arranque en frío siguen condicionando el tiempo de respuesta del servidor. Para una visita completamente sin red se necesita haber descargado antes los archivos; este cambio no convierte el sistema en una aplicación de ventas offline.
