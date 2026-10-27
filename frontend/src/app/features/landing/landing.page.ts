import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, of } from 'rxjs';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { BRAND } from '../../core/brand';
import { BrandMarkComponent } from '../../shared/components/brand-mark.component';
import { WaveArtComponent } from '../../shared/components/wave-art.component';
import { BillingApi, Plan } from '../billing/billing.api';
import { PlanCardComponent } from '../billing/plan-card.component';

@Component({
  selector: 'app-landing-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, BrandMarkComponent, WaveArtComponent, PlanCardComponent],
  templateUrl: './landing.page.html',
})
export class LandingPage {
  protected readonly auth = inject(AuthService);
  protected readonly brand = BRAND;
  protected readonly year = new Date().getFullYear();
  /** Planes configurados por la plataforma; sin precios inventados en el código. */
  protected readonly plans = toSignal(inject(BillingApi).publicPlans().pipe(catchError(() => of<Plan[]>([]))));

  protected readonly benefits = [
    {
      title: 'Cobros directos, sin intermediarios',
      text: 'Tus compradores te pagan por Yape o Plin a tus propias cuentas. Nosotros no tocamos tu dinero.',
    },
    {
      title: 'Cada vencimiento bajo control',
      text: 'Fechas de activación, vencimiento y renovación calculadas para cada cliente, sin hojas de cálculo.',
    },
    {
      title: 'Compras en menos de un minuto',
      text: 'Tus clientes eligen, pagan y te escriben por WhatsApp sin crear cuentas ni contraseñas.',
    },
    {
      title: 'Cupos sin sobreventa',
      text: 'Si un servicio tiene cupos limitados, la disponibilidad se controla en el servidor y nunca vendes dos veces el último.',
    },
  ];

  protected readonly steps = [
    { title: 'Crea tu negocio', text: 'Regístrate y obtén tu enlace público exclusivo.' },
    { title: 'Publica tus productos', text: 'Define precios en soles, duración y cupos.' },
    { title: 'Comparte tu enlace', text: 'Por WhatsApp, redes sociales o donde vendas.' },
    { title: 'Verifica y renueva', text: 'Confirma pagos y gestiona renovaciones desde tu panel.' },
  ];

  protected readonly features = [
    { group: 'Ventas', items: ['Tienda pública con tu logo y colores', 'Pedidos con código único', 'Verificación manual de pagos Yape y Plin', 'Mensaje prellenado a tu WhatsApp'] },
    { group: 'Clientes', items: ['Registro de clientes que ya tenías', 'Historial de compras y renovaciones', 'Búsqueda por nombre o celular', 'Preferencias de comunicación'] },
    { group: 'Control', items: ['Ingresos confirmados separados de pendientes', 'Suscripciones por vencer y vencidas', 'Reportes por periodo y producto', 'Auditoría de aprobaciones'] },
  ];

  protected readonly faqs = [
    {
      q: '¿Mis compradores necesitan crear una cuenta?',
      a: 'No. Solo indican su nombre y celular, eligen Yape o Plin y registran su código de operación. Nunca les pedimos contraseña.',
    },
    {
      q: '¿La plataforma recibe el dinero de mis ventas?',
      a: 'No. Tus compradores pagan directamente a las cuentas Yape o Plin que tú configures. Tu suscripción a la plataforma es un cobro separado.',
    },
    {
      q: '¿Los pagos se verifican automáticamente?',
      a: 'Hoy no. Yape y Plin no ofrecen una verificación pública para terceros, así que tú confirmas cada pago desde tu panel tras revisarlo en tu aplicación. Un comprobante nunca aprueba un pago por sí solo.',
    },
    {
      q: '¿Puedo registrar clientes que ya tenía?',
      a: 'Sí. Puedes cargar suscripciones existentes con su fecha de inicio y vencimiento, sin contarlas como ventas nuevas.',
    },
    {
      q: '¿Se envían recordatorios automáticos por WhatsApp?',
      a: 'Al inicio, el panel prepara los recordatorios y tú los envías con un toque desde tu WhatsApp. El envío automático requiere una integración oficial con WhatsApp Business que se habilitará más adelante.',
    },
  ];
}
