# Rutas del frontend (Angular)

Todas las funcionalidades se cargan de forma diferida (`loadComponent` / `loadChildren`).
Componentes standalone, `ChangeDetectionStrategy.OnPush`, signals para estado local.

| Ruta | Feature | Acceso | Fase |
|---|---|---|---|
| `/` | `landing` | público | 1 |
| `/ingresar` | `auth/login` | solo invitados | 1 |
| `/registro` | `auth/register` | solo invitados | 1 |
| `/recuperar` | `auth/forgot-password` | solo invitados | 1 |
| `/restablecer?token=` | `auth/reset-password` | público | 1 |
| `/panel` | `dashboard` (shell con menú lateral) | `BUSINESS_OWNER` | 1 |
| `/panel/productos` | `products` | owner | 2 |
| `/panel/productos/nuevo`, `/panel/productos/:id` | `products` | owner | 2 |
| `/panel/clientes`, `/panel/clientes/nuevo`, `/panel/clientes/:id` | `customers` | owner | 4 |
| `/panel/ventas`, `/panel/ventas/:id` | `orders` + `payments` | owner | 3 |
| `/panel/suscripciones` | `subscriptions` | owner | 4 |
| `/panel/renovaciones` | `renewals` | owner | 5 |
| `/panel/configuracion` (negocio, pagos, WhatsApp, plan) | `settings` | owner | 2 / 6 |
| `/tienda/:slug` | `storefront` | público | 3 |
| `/tienda/:slug/comprar/:productId` | `checkout` | público | 3 |
| `/pedido/:publicToken` | `checkout/order-status` | público | 3 |
| `/renovar/:token` | `renewals/public` | público | 5 |
| `/admin` | `super-admin` (shell propio) | `SUPER_ADMIN` | 6 |
| `**` | página 404 | público | 1 |

Guards:

* `authGuard` — requiere sesión; redirige a `/ingresar?volver=…`.
* `guestGuard` — si ya hay sesión, redirige al panel correspondiente.
* `roleGuard(role)` — verifica el rol devuelto por `/auth/me` (la autorización real la hace el backend).

El menú lateral del panel solo muestra secciones ya implementadas. Las secciones de fases futuras se
añaden cuando su backend y frontend estén funcionando (no hay enlaces decorativos).
