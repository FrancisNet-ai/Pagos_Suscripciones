# Módulos del backend y sus dependencias

Cada paquete de primer nivel de `com.nuvyra` es un módulo. La prueba
[`ArchitectureTest`](../../backend/src/test/kotlin/com/nuvyra/ArchitectureTest.kt) revisa estas reglas en cada CI.

## Reglas
1. **`common` es la base y no depende de nadie.** Errores, auditoría, configuración, validación, texto, web,
   persistencia, tenencia, correo y enlaces de WhatsApp.
2. **Las dependencias van hacia abajo.** Un módulo puede usar los de su nivel o de niveles inferiores, nunca los de
   arriba.
3. **Los módulos de borde se pueden quitar sin romper el resto:** nadie los importa. Son `admin`, `imports`,
   `reports`, `staff`, `supportaccess`, `tasks` y `web`.
4. **Cuando un módulo de abajo necesita algo de uno de arriba, lo pide con una interfaz (puerto)** y el de arriba la
   implementa. Ejemplo: `businesses.BrandingGate`, que implementa `saas.PlanBrandingGate`. Si el módulo de arriba
   no existe, el de abajo funciona igual.
5. **Cada módulo registra sus propios interceptores** (`StaffWebConfig`, `SaasWebConfig`, `TwoFactorWebConfig`).
   `security` no los conoce.

## Niveles

| Nivel | Módulos |
|---|---|
| Base | `common` |
| Identidad | `users`, `membership`, `security`, `twofactor`, `auth` |
| Negocio | `businesses`, `media`, `customers`, `notifications` |
| Ventas (nudo pendiente) | `products`, `orders`, `payments`, `subscriptions`, `renewals`, `storefront`, `saas`, `whatsapp` |
| Borde | `admin`, `customeradmin`, `imports`, `reports`, `staff`, `supportaccess`, `tasks`, `web` |

## Estado
- **Antes:** 17 dependencias circulares, con 19 de 26 módulos dentro de un solo nudo.
- **Ahora:** 4 dependencias circulares directas, con 8 módulos dentro del nudo de ventas. El núcleo de identidad y
  negocio ya no tiene ciclos.
- **Cambios para llegar aquí:**
  - Los tipos de membresía pasaron a `membership`, `ClientInfo` a `common.web`, `TenantContextFilter` a
    `security`, y el envío manual por WhatsApp a `common.messaging`.
  - Los textos de pedidos pasaron a `orders`, el panel de clientes a `customeradmin` y los cupos se unieron a
    `products`.

## Pendiente: deshacer el nudo de ventas
Las dependencias circulares que quedan son `orders ↔ payments`, `orders ↔ subscriptions`,
`payments ↔ renewals` y `renewals ↔ whatsapp`. Se resuelven con **eventos de dominio** (`ApplicationEventPublisher`
de Spring):
- `payments` publica «pago verificado» en lugar de llamar a `orders`, `subscriptions` y `renewals`.
- `renewals` publica «aviso listo» y `whatsapp` lo envía si está instalado.

Al sacar un módulo del nudo, hay que quitarlo de `knownKnot` en `ArchitectureTest`.

El frontend ya no tiene dependencias circulares entre secciones (`features/`).
