import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { StaffPermission } from '../../core/auth/auth.models';
import { AuthService } from '../../core/auth/auth.service';
import { IconComponent } from '../../shared/components/icon.component';
import { OrdersApi } from '../orders/orders.api';

interface Shortcut {
  permission: StaffPermission;
  icon: string;
  title: string;
  text: string;
  route: string;
}

const SHORTCUTS: Shortcut[] = [
  { permission: 'ORDERS', icon: 'wallet', title: 'Pedidos', text: 'Revisa pedidos y verifica pagos de Yape y Plin.', route: '/panel/ventas' },
  { permission: 'PRODUCTS', icon: 'box', title: 'Productos', text: 'Actualiza el catálogo, precios y cupos.', route: '/panel/productos' },
  { permission: 'CUSTOMERS', icon: 'users', title: 'Clientes', text: 'Consulta clientes y sus suscripciones.', route: '/panel/clientes' },
  { permission: 'RENEWALS', icon: 'renew', title: 'Renovaciones', text: 'Envía los avisos de renovación pendientes.', route: '/panel/renovaciones' },
  { permission: 'REPORTS', icon: 'chart', title: 'Reportes', text: 'Ingresos confirmados y ventas del negocio.', route: '/panel/reportes' },
];

/** Inicio del colaborador: accesos a sus secciones, sin datos que el titular no le habilitó. */
@Component({
  selector: 'app-staff-home-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, IconComponent],
  template: `
    <section aria-labelledby="inicio-titulo" class="mx-auto max-w-6xl">
      <div class="dashboard-welcome">
        <div>
          <p class="type-eyebrow">{{ auth.business()?.businessName }}</p>
          <h1 id="inicio-titulo" class="type-h1 mt-2">Hola, {{ firstName() }}.</h1>
          <p>Estas son las secciones que el titular te habilitó.</p>
        </div>
      </div>
      @if (toVerify(); as count) {
        <a
          routerLink="/panel/ventas"
          [queryParams]="{ filtro: 'TO_VERIFY' }"
          class="notice notice-warning mb-4 flex-wrap items-center justify-between no-underline"
          ><span>{{ count }} {{ count === 1 ? 'pago espera' : 'pagos esperan' }} verificación.</span
          ><span class="inline-flex items-center gap-2 text-sm font-semibold">Revisar pagos <app-icon name="arrow" /></span
        ></a>
      }
      <ul class="mt-6 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
        @for (s of shortcuts(); track s.route) {
          <li>
            <a [routerLink]="s.route" class="dashboard-surface block h-full no-underline">
              <span class="surface-heading">
                <span class="font-semibold text-night">{{ s.title }}</span><app-icon [name]="s.icon" />
              </span>
              <span class="mt-2 block text-sm text-[var(--text-muted)]">{{ s.text }}</span>
            </a>
          </li>
        }
      </ul>
      <p class="mt-8 text-sm text-[var(--text-muted)]">
        ¿Necesitas otra sección? Pídesela al titular del negocio.
      </p>
    </section>
  `,
})
export class StaffHomePage {
  protected readonly auth = inject(AuthService);

  protected readonly firstName = computed(() => this.auth.user()?.fullName.split(' ')[0] ?? '');
  protected readonly shortcuts = computed(() => SHORTCUTS.filter((s) => this.auth.can(s.permission)));

  protected readonly toVerify = toSignal(
    this.auth.can('ORDERS')
      ? inject(OrdersApi)
          .summary()
          .pipe(
            map((s) => s.toVerify),
            catchError(() => of(0)),
          )
      : of(0),
    { initialValue: 0 },
  );
}
