# Textos legales de Nuvyra — borradores para revisión

**Estado:** borrador técnico, **no** revisado por un abogado. No se publica en la aplicación hasta que un
profesional lo revise y se completen los datos marcados entre corchetes (`[…]`).

Los borradores describen lo que el sistema hace hoy, según el código del repositorio (datos que se piden,
consentimientos que se registran, proveedores y plazos de conservación ya programados). Si el producto
cambia, hay que actualizarlos.

| Documento | Para quién |
|---|---|
| [Términos y condiciones](terminos-y-condiciones.md) | Vendedores que contratan Nuvyra |
| [Política de privacidad](politica-de-privacidad.md) | Vendedores, colaboradores y compradores |
| [Política de conservación y eliminación](politica-de-conservacion.md) | Uso interno y anexo de la política de privacidad |

## Datos que el titular debe completar
- `[NOMBRE O RAZÓN SOCIAL]`, `[RUC o DNI]` y `[DOMICILIO]` de quien presta el servicio.
- `[CORREO DE CONTACTO]`: se sugiere el correo del proyecto.
- `[FECHA DE VIGENCIA]` y `[REGIÓN DEL SERVIDOR]` (la región de Oracle Cloud que se elija).
- Precio, periodicidad y condiciones de los planes (se publican en la página de tarifas).

## Preguntas para el abogado
1. **Normativa vigente:** confirmar la Ley N.º 29733 y su reglamento vigente (D. S. N.º 016-2024-JUS) y
   ajustar las citas.
2. **Roles:** Nuvyra actúa como **responsable** de los datos de vendedores y colaboradores, y como
   **encargada del tratamiento** de los datos de compradores, que pertenecen a cada vendedor. ¿Basta la
   cláusula de encargo incluida en los términos o hace falta un contrato aparte?
3. **Bancos de datos:** ¿qué bancos de datos personales deben inscribirse ante la Autoridad Nacional de
   Protección de Datos Personales y quién los inscribe (Nuvyra o cada vendedor)?
4. **Flujo transfronterizo:** el servidor estará en `[REGIÓN DEL SERVIDOR]` (Brasil, Chile o Colombia) y se
   usan Meta (WhatsApp) y Google (correo). ¿Qué hace falta además de informarlo?
5. **Consumidor:** si un vendedor persona natural o microempresa se considera consumidor frente a Nuvyra,
   ¿aplica el Código de Protección y Defensa del Consumidor (Ley N.º 29571) y el Libro de Reclamaciones
   virtual?
6. **Comprobantes:** Nuvyra aún no emite comprobantes electrónicos SUNAT por los planes. ¿Qué debe decir el
   texto mientras tanto?
7. **Plazos de conservación** propuestos en la política de conservación: ¿son adecuados (en especial los
   tributarios y los de comprobantes de pago)?
8. **Limitación de responsabilidad** y **jurisdicción**: validar las cláusulas propuestas.

## Después de la revisión
1. Reemplazar los resúmenes de `frontend/src/app/features/legal/legal.page.ts` por los textos definitivos y
   quitar el aviso de «Versión preliminar».
2. Si cambia el texto que aceptan los compradores, subir `CONSENT_POLICY_VERSION`
   (`backend/.../customers/CustomerService.kt`) para que los nuevos consentimientos registren la versión
   correcta.
3. Inscribir los bancos de datos que indique el abogado.
