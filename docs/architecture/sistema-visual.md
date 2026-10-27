# Sistema visual

## Paleta y tokens

Definidos como variables CSS en `frontend/src/styles/tokens.css` y expuestos a Tailwind con `@theme`.

| Token | Valor | Uso |
|---|---|---|
| `--color-night` | `#0A1931` | Navegación oscura, cabeceras premium, botón principal, identidad |
| `--color-corporate` | `#1A3D63` | Botones secundarios, elementos interactivos, zonas destacadas |
| `--color-steel` | `#4A7EA7` | Acentos, indicadores, selección, gráficos discretos, foco |
| `--color-mist` | `#B3CFE5` | Superficies suaves, separadores, apoyo |
| `--color-snow` | `#F6FAFD` | Superficies claras, lectura, formularios |
| `--color-shadow` | `#101014` | Secciones oscuras especiales, elementos editoriales |

Derivados semánticos (para no usar los seis colores con la misma intensidad):

| Token semántico | Valor |
|---|---|
| `--surface-page` | `#F6FAFD` |
| `--surface-raised` | `#FFFFFF` |
| `--surface-inverse` | `#0A1931` |
| `--text-strong` | `#0A1931` |
| `--text-body` | `#1A3D63` con 85 % |
| `--text-muted` | `#3B6A8F` (tono derivado de `#4A7EA7` para alcanzar 5.5:1 en texto pequeño) |
| `--line-subtle` | `#B3CFE5` al 60 % |
| `--focus-ring` | `#4A7EA7` |
| `--success` / `--warning` / `--danger` | `#1F7A5A` / `#855C0E` / `#A23B3B` (desaturados para armonizar) |

Contraste verificado (WCAG): `#0A1931` sobre `#F6FAFD` ≈ 16.7:1; `#F6FAFD` sobre `#1A3D63` ≈ 10:1;
`#4A7EA7` sobre `#F6FAFD` ≈ 4.1:1 (usar solo para texto grande o no esencial).

## Tipografía

| Rol | Deseada | En uso (licencia libre OFL) | Motivo |
|---|---|---|---|
| Display / titulares principales | Voga | **Playfair Display** | No se dispone de archivos de Voga con licencia verificada para SaaS. Playfair es una serif de alto contraste de apariencia editorial similar. |
| Títulos secundarios / editorial | Bodoni FLF | **Bodoni Moda** | Bodoni FLF no tiene licencia de distribución web verificada. Bodoni Moda (OFL) es una Bodoni moderna optimizada para pantalla. |
| Interfaz | Inter | **Inter** (OFL) | — |

Las fuentes se empaquetan con `@fontsource-variable/*` (autoalojadas, sin llamadas a terceros).
Para usar Voga o Bodoni FLF en el futuro basta con cambiar `--font-display` y `--font-editorial`
en `tokens.css` una vez se adquiera una licencia web/app adecuada; los archivos comerciales no deben
subirse a repositorios públicos.

Escala (fluida con `clamp`): display 2.75–5 rem · h1 2.25–3.25 rem · h2 1.75–2.25 rem · h3 1.25 rem ·
cuerpo 1 rem · pequeño 0.875 rem · micro 0.75 rem. Importes y fechas con `font-variant-numeric: tabular-nums`.

## Principios de composición

* Editorial antes que "tarjetas": jerarquía tipográfica, espacios amplios, separadores de 1 px
  (`--line-subtle`) y secciones abiertas. Contenedores solo para tablas, formularios, estados y gráficos.
* Radios suaves (`6px` controles, `14px` paneles), sombras mínimas (`0 1px 2px rgb(10 25 49 / 6%)`).
* Fondo decorativo de ondas azules (SVG inline) solo en landing y autenticación, con baja opacidad.
* Transiciones de 150–220 ms con `ease-out`; respetar `prefers-reduced-motion`. Sin animaciones en el checkout.
* Táctil: objetivos de al menos 44 × 44 px; formularios de una columna en móvil.
* Foco visible siempre (`outline` de 2 px `--focus-ring`).
