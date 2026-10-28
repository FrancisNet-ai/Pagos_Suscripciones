import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, of } from 'rxjs';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { IconComponent } from '../../shared/components/icon.component';
import { BRAND } from '../../core/brand';
import { BrandMarkComponent } from '../../shared/components/brand-mark.component';
import { WaveArtComponent } from '../../shared/components/wave-art.component';
import { BillingApi, Plan } from '../billing/billing.api';
import { PlanCardComponent } from '../billing/plan-card.component';

@Component({
  selector: 'app-landing-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, BrandMarkComponent, WaveArtComponent, PlanCardComponent, IconComponent],
  templateUrl: './landing.page.html',
})
export class LandingPage {
  protected readonly auth = inject(AuthService);
  protected readonly brand = BRAND;
  protected readonly year = new Date().getFullYear();
  /** Planes configurados por la plataforma; sin precios inventados en el código. */
  protected readonly plans = toSignal(
    inject(BillingApi)
      .publicPlans()
      .pipe(catchError(() => of<Plan[]>([]))),
  );

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
}
