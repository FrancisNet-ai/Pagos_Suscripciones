import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, of, startWith } from 'rxjs';
import { ApiError } from '../../core/services/api-error';
import { PaymentMethodFormComponent } from './payment-method-form.component';
import { PaymentMethod, SettingsApi } from './settings.api';

type State = { status: 'loading' } | { status: 'error'; message: string } | { status: 'ready'; methods: PaymentMethod[] };

@Component({
  selector: 'app-payment-methods-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [PaymentMethodFormComponent],
  template: `
    <div class="grid gap-10">
      <div class="max-w-2xl">
        <h2 class="font-[family-name:var(--font-editorial)] text-2xl font-semibold">Cobros con Yape y Plin</h2>
        <p class="mt-3">
          Tus compradores te pagan directamente a estos números. Pueden tener titulares y números distintos, y no tienen por qué coincidir con tu WhatsApp.
        </p>
        <p class="notice notice-info mt-5" role="note">
          Mostraremos el nombre como «titular declarado» por ti: no lo validamos con el banco. Cada pago se confirma manualmente desde tu panel.
        </p>
      </div>

      @let s = state();
      @if (s.status === 'loading') {
        <div class="h-40 animate-pulse rounded bg-mist/30" aria-busy="true"><span class="sr-only">Cargando…</span></div>
      } @else if (s.status === 'error') {
        <p class="notice notice-error" role="alert">{{ s.message }}</p>
      } @else {
        @for (method of s.methods; track method.method) {
          <app-payment-method-form [initial]="method" />
        }
      }
    </div>
  `,
})
export class PaymentMethodsPage {
  protected readonly state = toSignal(
    inject(SettingsApi)
      .paymentMethods()
      .pipe(
        map((methods): State => ({ status: 'ready', methods })),
        catchError((e: unknown) => of<State>({ status: 'error', message: ApiError.from(e).message })),
        startWith<State>({ status: 'loading' }),
      ),
    { requireSync: true },
  );
}
