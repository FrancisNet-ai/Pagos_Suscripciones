# Textos legales de Nuvyra

Los **Términos y condiciones** y la **Política de privacidad** se publican en `/legal/condiciones` y
`/legal/privacidad`. El texto vive en un solo lugar:
[`frontend/src/app/features/legal/legal-documents.ts`](../../frontend/src/app/features/legal/legal-documents.ts).

Describen lo que el sistema hace hoy: datos que pide, consentimientos que registra, proveedores y plazos ya
programados. Si el producto cambia, hay que actualizarlos.

**No los revisó un abogado.** Se redactaron siguiendo la normativa peruana:
- Ley N.º 29733 y su Reglamento (D. S. N.º 016-2024-JUS): información al titular, finalidades, encargados,
  flujo transfronterizo, conservación, seguridad, incidentes y derechos.
- Código de Protección y Defensa del Consumidor (Ley N.º 29571): información clara, reclamos e Indecopi.
- Contratación electrónica: Código Civil, art. 141-A, y Ley N.º 27291.

## Pendiente del titular
1. Completar en `LEGAL_HOLDER` (en `legal-documents.ts`) los datos que hoy se ven como «[por completar]»:
   - nombre o razón social;
   - RUC (o DNI);
   - domicilio.

   Mientras falten, las páginas muestran un aviso.
2. Inscribir el banco de datos «Usuarios de Nuvyra» en el Registro Nacional de Protección de Datos Personales
   y poner su código en `dataBankCode`.
3. Revisión de un abogado. Puntos a confirmar:
   - plazos de respuesta a los derechos;
   - cláusulas de devolución, limitación de responsabilidad y jurisdicción;
   - si hace falta un contrato de encargo aparte con cada vendedor;
   - comprobantes de pago según el régimen tributario.
4. **Libro de Reclamaciones virtual.** Si a los vendedores microempresarios se les considera consumidores
   (Ley N.º 29571), puede ser obligatorio. Hoy los reclamos se reciben por correo.

## Al cambiar un texto
- Actualizar `LEGAL_VERSION` (la fecha que muestra la página).
- Si cambia lo que aceptan los compradores, subir `CONSENT_POLICY_VERSION` en
  `backend/.../customers/CustomerService.kt` (hoy `2026-11`), para que los consentimientos nuevos registren la
  versión correcta.
- Avisar a los vendedores con 15 días de anticipación si el cambio es importante (sección 15 de los términos).

## Conservación
[Política de conservación y eliminación](politica-de-conservacion.md): plazos implementados y pendientes (uso
interno). La política de privacidad publicada solo menciona los plazos que el sistema ya cumple.
