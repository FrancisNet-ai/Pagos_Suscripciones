import { BRAND } from '../../core/brand';

/**
 * Textos legales publicados en /legal/condiciones y /legal/privacidad.
 *
 * Describen lo que el sistema hace hoy (datos que pide, consentimientos que registra, proveedores y plazos ya
 * programados). Si el producto cambia, hay que actualizarlos y subir LEGAL_VERSION. Si cambia lo que aceptan
 * los compradores, subir también CONSENT_POLICY_VERSION en el backend (customers/CustomerService.kt).
 */

/** Datos de quien presta el servicio. `null` = pendiente: la página lo muestra como «por completar». */
export const LEGAL_HOLDER = {
  name: 'Kevin Francisco Cabrel Peralta' as string | null,
  /** RUC (o DNI si aún no tiene RUC). */
  taxId: 'DNI N.º 70491701' as string | null,
  address: 'Huacho, provincia de Huaura, departamento de Lima' as string | null,
  email: 'nuvyra.suppor@hotmail.com',
  /** Código de inscripción del banco de datos en el Registro Nacional de Protección de Datos Personales. */
  dataBankCode: null as string | null,
};

export const LEGAL_VERSION = '9 de noviembre de 2026';

export interface LegalSection {
  heading: string;
  paragraphs?: string[];
  items?: string[];
  /** Párrafos después de la lista. */
  after?: string[];
}

export interface LegalDoc {
  title: string;
  intro: string;
  sections: LegalSection[];
}

const N = BRAND.name;
const pending = '[por completar]';
const holder = () => LEGAL_HOLDER.name ?? pending;
const holderId = () =>
  `${LEGAL_HOLDER.name ?? pending}, identificado con ${LEGAL_HOLDER.taxId ?? pending}, con domicilio en ${LEGAL_HOLDER.address ?? pending}, Perú`;

export const legalHolderComplete = () => !!(LEGAL_HOLDER.name && LEGAL_HOLDER.taxId && LEGAL_HOLDER.address);

export function legalDocs(): Record<string, LegalDoc> {
  const email = LEGAL_HOLDER.email;
  return {
    condiciones: {
      title: 'Términos y condiciones',
      intro:
        `Estos términos regulan el uso de ${N}: por los vendedores que lo contratan y sus colaboradores, y por los ` +
        'compradores que hacen pedidos en las tiendas creadas con él. Léelos con atención: al crear una cuenta, ' +
        'aceptar una invitación o hacer un pedido, los aceptas.',
      sections: [
        {
          heading: '1. Quién presta el servicio',
          paragraphs: [
            `El servicio ${N} lo presta ${holderId()} (en adelante, «${N}»).`,
            `Contacto: ${email}. También puedes escribirnos desde la sección «Soporte» del panel.`,
          ],
        },
        {
          heading: '2. Aceptación y contratación por medios electrónicos',
          paragraphs: [
            'La aceptación se da al marcar la casilla correspondiente y continuar, conforme al artículo 141-A del ' +
              'Código Civil y a la Ley N.º 27291. Registramos la fecha y la versión de lo aceptado.',
            'Para crear una cuenta de vendedor o de colaborador debes ser mayor de 18 años y tener capacidad para ' +
              'contratar. Si actúas en nombre de una empresa, declaras que tienes facultades para obligarla.',
          ],
        },
        {
          heading: `3. Qué es ${N} y qué no es`,
          paragraphs: [
            `${N} es un software en línea que los vendedores contratan por periodos para administrar la venta de ` +
              'suscripciones y productos digitales: catálogo, tienda en línea, pedidos, verificación de pagos, ' +
              'clientes, vencimientos y avisos de renovación.',
          ],
          items: [
            `${N} no vende los productos de los vendedores ni es parte de esas ventas. El contrato de compra es entre ` +
              'el comprador y el vendedor.',
            'Los compradores pagan directamente al vendedor por Yape, Plin u otros medios que el vendedor configure. ' +
              `${N} no recibe, retiene, custodia ni transfiere ese dinero.`,
            `${N} no verifica pagos automáticamente: el vendedor decide si un pago es válido. Un comprobante o código ` +
              'enviado por el comprador no confirma el pago por sí solo.',
            `${N} no garantiza la calidad, la legalidad ni la entrega de los productos de los vendedores.`,
          ],
        },
        {
          heading: '4. Cuenta del vendedor y colaboradores',
          items: [
            'Los datos de registro deben ser verdaderos y mantenerse actualizados.',
            'Eres responsable de la confidencialidad de tu contraseña y de lo que se haga desde tu cuenta. ' +
              'Puedes activar la verificación en dos pasos y cerrar las sesiones abiertas desde «Mi cuenta».',
            'El titular puede invitar colaboradores y elegir a qué secciones acceden, dentro del límite de usuarios ' +
              'de su plan. El titular responde por lo que hagan sus colaboradores en la cuenta.',
            'Una misma persona puede administrar varios negocios. Cada negocio y sus datos son independientes.',
            `Si detectas un acceso no autorizado, cambia tu contraseña y avísanos de inmediato a ${email}.`,
          ],
        },
        {
          heading: '5. Planes, precios y pagos',
          items: [
            'Los planes, lo que incluye cada uno (límites y funciones) y su precio total en soles se muestran en la ' +
              'página de inicio y en «Mi plan» antes de pagar.',
            `El pago se hace a las cuentas de ${N} indicadas en «Mi plan». El plan se activa cuando ${N} verifica el ` +
              'pago y dura el periodo pagado (por ejemplo, un mes).',
            `No hay cobros automáticos: ${N} no guarda tarjetas ni debita dinero. Para continuar, pagas cada nuevo ` +
              'periodo. Si pagas por adelantado, el nuevo periodo se suma al vencimiento actual.',
            'Al vencer el plan hay un plazo de gracia, indicado en «Mi plan», en el que la cuenta sigue funcionando. ' +
              'Después, la cuenta queda en modo de solo lectura y la tienda deja de recibir pedidos hasta que pagues. ' +
              'Tus datos no se eliminan por la sola falta de pago.',
            `Los cambios de precio se anuncian con al menos 15 días de anticipación y no afectan los periodos ya pagados.`,
            'El comprobante de pago que corresponda según el régimen tributario del prestador se emite a solicitud ' +
              `del vendedor, escribiendo a ${email} con los datos del pago.`,
          ],
        },
        {
          heading: '6. Cancelación y devoluciones del plan',
          items: [
            'Puedes dejar de usar el servicio cuando quieras: basta con no pagar el siguiente periodo.',
            `Los periodos pagados no se devuelven, salvo que el servicio haya estado indisponible por causas ` +
              `atribuibles a ${N} durante una parte importante del periodo, o que la ley lo exija. En esos casos ` +
              'se devuelve la parte proporcional o se extiende el plan, a elección del vendedor.',
            `Si pagaste por error un monto distinto o un plan que no querías, escríbenos a ${email} dentro de los ` +
              '7 días siguientes.',
          ],
        },
        {
          heading: '7. Obligaciones del vendedor',
          items: [
            'Vender solo productos y servicios lícitos sobre los que tengas derecho, y respetar las condiciones de ' +
              'los proveedores de esos servicios (plataformas de streaming, software u otros). Revender o compartir ' +
              'cuentas que el proveedor prohíbe es de tu exclusiva responsabilidad.',
            'Informar a tus compradores, de forma clara y veraz, el precio total, la duración, las condiciones y las ' +
              'restricciones de lo que vendes, conforme al Código de Protección y Defensa del Consumidor ' +
              '(Ley N.º 29571).',
            'Entregar lo vendido y atender los reclamos, cambios y devoluciones de tus compradores. Si corresponde ' +
              'por tu actividad, tener tu propio Libro de Reclamaciones y emitir tus comprobantes de pago.',
            'Tratar los datos de tus compradores conforme a la Ley N.º 29733 y usarlos solo para lo que ellos ' +
              'autorizaron (ver la sección 10).',
            'Mantener actualizados tus datos de cobro (titular y celular de Yape o Plin).',
          ],
        },
        {
          heading: '8. Usos prohibidos',
          items: [
            'Vender productos ilícitos, falsificados, robados u obtenidos de forma fraudulenta.',
            'Enviar mensajes masivos o publicidad a personas que no lo autorizaron.',
            'Suplantar a otra persona o negocio, o usar datos de pago de terceros.',
            'Intentar acceder a datos de otros negocios, vulnerar la seguridad del servicio o sobrecargarlo.',
            'Copiar, revender o descompilar el software.',
          ],
        },
        {
          heading: '9. Compras en las tiendas (para compradores)',
          items: [
            `Al hacer un pedido, compras al vendedor de esa tienda, no a ${N}. El vendedor es responsable del ` +
              'producto, de su entrega y de atenderte.',
            'Pagas directamente al vendedor. Tu pedido queda confirmado solo cuando el vendedor verifica tu pago.',
            'Los pedidos no pagados vencen después del plazo que indica la página del pedido, y el cupo reservado se ' +
              'libera.',
            'No necesitas crear una cuenta: tu nombre y tu celular identifican tus pedidos. Usa el número donde ' +
              'tienes WhatsApp, porque por ahí te contactará el vendedor.',
            'Para reclamos sobre lo que compraste, dirígete primero al vendedor. Si no te responde, escríbenos a ' +
              `${email} con tu número de pedido y te ayudaremos a contactarlo. También puedes acudir al Indecopi.`,
          ],
        },
        {
          heading: '10. Datos personales',
          paragraphs: [
            'El tratamiento de datos personales se explica en la Política de privacidad, que forma parte de estos ' +
              'términos.',
            'Los datos de los compradores pertenecen al vendedor con quien compraron: el vendedor es el responsable ' +
              `de ese tratamiento y ${N} lo realiza por su encargo, solo para prestarle el servicio. Por este encargo, ` +
              `${N} se compromete a:`,
          ],
          items: [
            'tratar esos datos solo para prestar el servicio y según las instrucciones del vendedor;',
            'no usarlos para fines propios ni compartirlos con otros vendedores o terceros, salvo con los proveedores ' +
              'necesarios indicados en la Política de privacidad;',
            'mantener medidas de seguridad, entre ellas el aislamiento entre negocios en la aplicación y en la base ' +
              'de datos, el cifrado de credenciales, copias de seguridad cifradas y el registro de accesos;',
            'ver los pedidos y clientes de un negocio solo con una autorización temporal del titular, que queda ' +
              'registrada y visible para él;',
            'avisar al vendedor, sin demora indebida, de cualquier incidente de seguridad que afecte sus datos;',
            'al terminar el servicio, devolver o eliminar los datos según la Política de privacidad, salvo los que la ' +
              'ley obligue a conservar.',
          ],
          after: [
            'El vendedor se compromete a obtener de sus compradores las autorizaciones necesarias. La tienda las ' +
              'pide al comprar y registra su finalidad, versión y fecha.',
          ],
        },
        {
          heading: '11. Disponibilidad del servicio',
          paragraphs: [
            `${N} procura que el servicio esté disponible de forma continua, con copias de seguridad diarias y ` +
              'monitoreo, pero no garantiza un funcionamiento sin interrupciones. Las actualizaciones pueden dejar el ' +
              'servicio sin respuesta por unos minutos; procuramos hacerlas en horarios de poco uso.',
          ],
        },
        {
          heading: '12. Suspensión y terminación',
          items: [
            `${N} puede suspender una cuenta que incumpla estos términos, informando el motivo al titular y dándole un ` +
              'plazo razonable para corregirlo.',
            'Ante fraude, uso ilícito o riesgo para otros usuarios, la suspensión puede ser inmediata.',
            'Si la suspensión no se debe a un incumplimiento tuyo, el tiempo suspendido se compensa en tu plan.',
            'Puedes pedir el cierre de tu cuenta y la eliminación de tus datos escribiendo a ' + email + '.',
          ],
        },
        {
          heading: '13. Propiedad intelectual',
          paragraphs: [
            `El software, la marca ${N} y sus diseños pertenecen a ${holder()}. El vendedor conserva los derechos ` +
              `sobre su marca, sus imágenes y sus contenidos, y autoriza a ${N} a mostrarlos en su tienda en línea ` +
              'mientras use el servicio.',
          ],
        },
        {
          heading: '14. Responsabilidad',
          items: [
            `${N} no responde por los productos, los cobros ni las obligaciones del vendedor frente a sus ` +
              'compradores, ni por pagos hechos a cuentas distintas de las indicadas.',
            `Salvo dolo o culpa inexcusable, la responsabilidad de ${N} frente al vendedor se limita al monto que este ` +
              'pagó por el plan en los últimos tres meses.',
            'Nada de lo anterior limita los derechos que la ley reconoce a los consumidores.',
          ],
        },
        {
          heading: '15. Cambios en estos términos',
          paragraphs: [
            'Podemos actualizar estos términos. Los cambios importantes se avisan con al menos 15 días de ' +
              'anticipación por correo o dentro del panel. Si no estás de acuerdo, puedes dejar de usar el servicio ' +
              'antes de que entren en vigor. La fecha de la versión vigente se indica al inicio.',
          ],
        },
        {
          heading: '16. Consultas y reclamos',
          paragraphs: [
            `Puedes enviar consultas, quejas o reclamos sobre ${N} a ${email}. Responderemos en un plazo máximo de ` +
              '15 días hábiles. Si eres consumidor y no estás conforme con la respuesta, puedes acudir al Indecopi.',
          ],
        },
        {
          heading: '17. Ley aplicable y controversias',
          paragraphs: [
            'Estos términos se rigen por las leyes de la República del Perú. Las controversias se intentarán resolver ' +
              'primero de forma directa. Si no se logra, se someterán a los jueces del distrito judicial del domicilio ' +
              'del prestador, sin perjuicio del derecho de los consumidores a acudir al Indecopi o a los jueces de su ' +
              'domicilio.',
          ],
        },
      ],
    },

    privacidad: {
      title: 'Política de privacidad',
      intro:
        `Esta política explica qué datos personales trata ${N}, para qué, con quién se comparten, cuánto tiempo se ` +
        'conservan y cómo ejercer tus derechos, conforme a la Ley N.º 29733, Ley de Protección de Datos Personales, ' +
        'y su Reglamento (Decreto Supremo N.º 016-2024-JUS).',
      sections: [
        {
          heading: '1. Responsable y contacto',
          paragraphs: [
            `Responsable: ${holderId()}.`,
            `Contacto para datos personales: ${email}.`,
            LEGAL_HOLDER.dataBankCode
              ? `Banco de datos: «Usuarios de ${N}», inscrito en el Registro Nacional de Protección de Datos ` +
                `Personales con el código ${LEGAL_HOLDER.dataBankCode}.`
              : `Banco de datos: «Usuarios de ${N}».`,
          ],
        },
        {
          heading: '2. Dos papeles distintos',
          items: [
            `Vendedores y colaboradores: ${N} es la responsable del tratamiento de sus datos de cuenta.`,
            `Compradores: el responsable es el vendedor con quien compraste. ${N} trata esos datos por encargo del ` +
              'vendedor y solo para prestarle el servicio. Para ejercer tus derechos, dirígete primero al vendedor; ' +
              `también puedes escribir a ${N}, que trasladará tu solicitud.`,
          ],
        },
        {
          heading: '3. Datos que tratamos y para qué',
          items: [
            'Vendedor (titular): nombre, correo, celular y datos del negocio (nombre, logo, portada, colores, ' +
              'WhatsApp comercial y cuentas Yape o Plin para cobrar). Para crear y administrar la cuenta y prestar el ' +
              'servicio contratado.',
            'Colaborador: nombre, correo, celular y secciones habilitadas. Para darle acceso al negocio que lo invitó.',
            'Vendedor y colaborador: contraseña (guardada solo como huella cifrada), inicios de sesión (fecha, ' +
              'dirección IP y navegador), sesiones abiertas y acciones relevantes. Para la seguridad de la cuenta, ' +
              'prevenir fraudes y atender incidentes.',
            'Vendedor y colaborador, si lo activan: secreto de la verificación en dos pasos (cifrado) y códigos de ' +
              'recuperación (solo su huella). Para proteger el acceso.',
            'Pagos del plan: método, monto, número de operación y comprobante. Para verificar el pago y activar el plan.',
            'Comprador: nombre y celular con WhatsApp. Para registrar el pedido con el vendedor y contactarlo sobre él.',
            'Comprador: pedido, monto, número de operación, titular que figura en el pago y, si la envía, la imagen ' +
              'del comprobante. Para que el vendedor verifique el pago.',
            'Comprador: suscripciones, fechas de vencimiento, renovaciones y respuestas a los avisos. Para avisar los ' +
              'vencimientos y gestionar las renovaciones.',
            'Comprador: las autorizaciones que dio al comprar, con su finalidad, versión y fecha. Para acreditar el ' +
              'consentimiento.',
          ],
          after: [
            'No tratamos datos de tarjetas, contraseñas de otros servicios ni datos sensibles. Los compradores no ' +
              'necesitan crear una cuenta.',
          ],
        },
        {
          heading: '4. Base del tratamiento y datos obligatorios',
          paragraphs: [
            'Tratamos los datos para ejecutar el servicio que contrataste o el pedido que hiciste, para cumplir ' +
              'obligaciones legales y, en lo opcional, con tu consentimiento.',
            'Los datos marcados como obligatorios en cada formulario son necesarios: sin ellos no se puede crear la ' +
              'cuenta ni registrar el pedido. Los opcionales no impiden usar el servicio.',
          ],
        },
        {
          heading: '5. Autorizaciones del comprador',
          items: [
            'Obligatorias para comprar: aceptar los términos de compra y autorizar el uso de tu nombre y celular para ' +
              'procesar el pedido.',
            'Opcionales: recibir por WhatsApp la confirmación de tu pago y de su verificación, y recibir avisos de ' +
              'vencimiento y renovación.',
          ],
          after: [
            'Puedes retirar las autorizaciones opcionales en cualquier momento: desde el enlace de renovación, ' +
              'pidiéndoselo al vendedor o escribiéndonos. Sin autorización no se envían esos mensajes automáticamente.',
          ],
        },
        {
          heading: '6. Con quién se comparten',
          paragraphs: [
            `${N} no vende ni cede datos personales. Solo acceden a ellos el vendedor que corresponda y los ` +
              'proveedores necesarios para prestar el servicio, que actúan como encargados:',
          ],
          items: [
            'Render (Estados Unidos): servidor donde funciona la aplicación.',
            'Supabase, sobre Amazon Web Services (Estados Unidos): base de datos y almacenamiento de imágenes y ' +
              'comprobantes.',
            'GitHub (Estados Unidos): copias de seguridad diarias de la base de datos, cifradas antes de guardarse.',
            'Brevo (Unión Europea): envío de correos de la cuenta, como la recuperación de contraseña y las ' +
              'invitaciones.',
            'Meta, WhatsApp Business Platform (Estados Unidos y otros): envío de mensajes, solo si el vendedor conecta ' +
              'su número y el comprador lo autorizó.',
            'Have I Been Pwned (internacional): para rechazar contraseñas filtradas se envían solo los 5 primeros ' +
              'caracteres de una huella de la contraseña, nunca la contraseña ni el correo.',
          ],
          after: [
            'Como estos proveedores están fuera del Perú, hay flujo transfronterizo de datos personales. Al aceptar ' +
              'esta política lo autorizas. Elegimos proveedores con medidas de seguridad y compromisos de ' +
              'confidencialidad adecuados.',
            'También podemos entregar datos a autoridades que los requieran conforme a ley.',
            `El personal de soporte de ${N} solo puede ver pedidos y clientes de un negocio con una autorización ` +
              'temporal del titular. Cada consulta queda registrada con su motivo y el titular puede verla.',
          ],
        },
        {
          heading: '7. Cuánto tiempo los conservamos',
          items: [
            'Datos de la cuenta y del negocio: mientras la cuenta exista. La falta de pago del plan no los elimina.',
            'Pedidos, pagos, suscripciones y comprobantes: mientras la cuenta del vendedor exista y, después, durante ' +
              'el plazo que exijan las normas tributarias y de protección al consumidor.',
            'Sesiones cerradas o vencidas: se eliminan 7 días después.',
            'Enlaces de recuperación de contraseña: vencen a los 30 minutos.',
            'Pedidos no pagados: vencen en el plazo indicado en la página del pedido.',
            'Copias de seguridad: se guardan cifradas durante 30 días.',
          ],
          after: [
            'Si pides la eliminación, borramos o anonimizamos los datos, salvo los que la ley obligue a conservar.',
          ],
        },
        {
          heading: '8. Seguridad',
          items: [
            'Conexión cifrada (HTTPS).',
            'Contraseñas guardadas solo como huella con BCrypt, rechazo de contraseñas filtradas y verificación en ' +
              'dos pasos.',
            'Credenciales de terceros y secretos de verificación cifrados con AES-256-GCM.',
            'Aislamiento entre negocios en la aplicación y en la base de datos.',
            'Límites de intentos y registro de acciones sensibles.',
            'Copias de seguridad diarias cifradas.',
          ],
          after: [
            'Si ocurre un incidente de seguridad que afecte tus datos, lo comunicaremos a la Autoridad Nacional de ' +
              'Protección de Datos Personales y a los afectados en los plazos y las formas que establece el Reglamento.',
          ],
        },
        {
          heading: '9. Tus derechos',
          paragraphs: [
            'Puedes ejercer tus derechos de información, acceso, rectificación, cancelación (supresión) y oposición, ' +
              'y retirar tu consentimiento en cualquier momento, sin efectos retroactivos.',
          ],
          items: [
            `Cómo: escribe a ${email} indicando tu nombre, tu celular o correo, lo que solicitas y, si lo tienes, el ` +
              'número de pedido. Podemos pedirte que acredites tu identidad.',
            'Plazo: responderemos dentro de los plazos que establece el Reglamento de la Ley N.º 29733.',
            'Si eres comprador: coordinaremos la respuesta con el vendedor responsable.',
            'Si no estás conforme: puedes presentar una reclamación ante la Autoridad Nacional de Protección de Datos ' +
              'Personales del Ministerio de Justicia y Derechos Humanos.',
          ],
        },
        {
          heading: '10. Menores de edad',
          paragraphs: [
            'Las cuentas de vendedor y colaborador son solo para mayores de 18 años. Si eres menor de edad, pide a tu ' +
              'padre, madre o tutor que haga la compra.',
          ],
        },
        {
          heading: '11. Cookies',
          paragraphs: [
            'Usamos solo dos cookies necesarias: una mantiene tu sesión iniciada y la otra protege los formularios ' +
              'contra envíos falsificados. No usamos cookies de publicidad ni de seguimiento.',
          ],
        },
        {
          heading: '12. Cambios en esta política',
          paragraphs: [
            'Publicaremos aquí cualquier cambio con su fecha. Si el cambio afecta lo que autorizaste, te pediremos ' +
              'una nueva autorización.',
          ],
        },
      ],
    },
  };
}
