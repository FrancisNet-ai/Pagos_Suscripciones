# Sistema visual de Nuvyra

Rediseño editorial del 27 de octubre de 2026, basado en la referencia visual proporcionada por el
propietario. Se aplica sobre las fases 1–7 existentes. La marca sigue siendo **Nuvyra**; «Voga» en la
referencia describe la dirección visual, no un cambio de nombre del producto.

## Identidad

| Token | Valor | Uso |
|---|---|---|
| night | `#0A1931` | Navegación, identidad y superficies oscuras |
| corporate | `#1A3D63` | Acciones y acentos principales |
| steel | `#4A7EA7` | Gráficos, detalles y estados de selección |
| mist | `#B3CFE5` | Separadores, superficies y arte de apoyo |
| snow | `#F6FAFD` | Fondo público y superficies de lectura |
| shadow | `#101014` | Contrastes oscuros específicos |

Los textos pequeños usan tonos derivados más oscuros para alcanzar contraste AA. El panel combina
un fondo azul muy claro, una barra lateral azul noche y superficies blancas discretas; la portada
combina espacio claro a la izquierda y mármol fluido azul a la derecha.

## Tipografía y recursos

- **Bodoni Moda:** logotipo tipográfico, titulares editoriales y mensajes destacados.
- **Inter:** menús, paneles, formularios, tablas, importes y fechas; números tabulares.
- **Playfair Display:** permanece disponible para compatibilidad con componentes existentes.
- Las familias son las alternativas OFL ya empaquetadas mediante Fontsource. No se incorporan Voga
  ni Bodoni FLF sin licencia de distribución comercial verificada.
- `frontend/public/art/nuvyra-fluid.webp`: ilustración original generada para este rediseño, sin marcas,
  texto ni controles. Se sirve desde el mismo origen; pesa aproximadamente 131 KiB y se reutiliza en
  portada, acceso y portada predeterminada de la tienda. El original generado se conserva fuera del repositorio.
- Los iconos son SVG lineales pequeños, decorativos y ocultos a lectores de pantalla; los controles
  siempre conservan etiquetas de texto o nombres accesibles.

## Organización

`frontend/src/styles/tokens.css` conserva las variables compartidas. `base.css` contiene las bases
existentes y `editorial.css` define las composiciones y componentes del rediseño, con bloques para
marketing, acceso, paneles, tienda y comprador y sus adaptaciones responsivas.

Los componentes compartidos de marca, ilustración, iconos, botones, campos, estados y gráficos evitan
variaciones entre Nuvyra Dashboard, Store y Billing. Los paneles mantienen su navegación y autorización
independientes para vendedores y SUPER_ADMIN.

## Composiciones

| Área | Tratamiento |
|---|---|
| Portada | Hero claro, titular editorial, arte fluido y piezas superpuestas de catálogo; beneficios abiertos, pasos, planes reales, FAQ y pie legal |
| Acceso | Fondo fluido envolvente, identidad a la izquierda y formulario claro a la derecha; registro y recuperación comparten estructura |
| Panel | Navegación compacta con iconos, cabecera contextual, indicadores Inter y gráfico de área basado en los ingresos reales |
| Productos y clientes | Filas y tablas legibles, búsqueda y filtros agrupados, acciones explícitas y tarjetas móviles existentes |
| Tienda | Marca del vendedor, portada propia o arte predeterminado, categorías y búsqueda derivadas del catálogo real, cuadrícula de productos |
| Checkout y pedido | Superficie compacta, progreso de pedido/pago/verificación y datos reales de Yape/Plin |
| Renovación | Estados diferenciados; el símbolo de confirmación solo aparece con `COMPLETED` |
| Facturación e integraciones | Mismo sistema de campos, superficies y navegación; se conservan contratación, importación y WhatsApp existentes |

Los planes y precios vienen de la API. La composición promocional de la portada se identifica como
ilustración y no contiene ingresos ficticios. Las tiendas conservan el logotipo, la portada y el color
configurados por el vendedor. No se añaden botones de autenticación social ni controles sin destino.

## Accesibilidad y móvil

- Formatos comprobados a 360, 390, 768 y 1440 píxeles, con formularios de una columna en móvil.
- Menús móviles con foco contenido, cierre por Escape y devolución del foco al botón que los abrió.
- Objetivos táctiles principales de al menos 44 píxeles y estilos de foco visibles.
- Gráfico con resumen y tabla equivalente para lectores de pantalla. La tabla se oculta mediante un
  contenedor de 1 píxel, evitando que su tamaño intrínseco provoque desbordamientos o espacio vacío.
- Mensajes de estado y errores conservan sus roles accesibles. La ilustración no comunica información esencial.
- Transiciones breves y desactivadas con `prefers-reduced-motion`; el checkout no introduce animaciones.

## Verificación

Ver [capturas comparativas y ejecución de pruebas](../design-review/README.md). El rediseño no cambia
contratos REST, migraciones ni reglas de cobro, cupos, autorización o mensajería.
