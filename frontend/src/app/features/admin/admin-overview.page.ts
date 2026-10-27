import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, map, of, startWith } from 'rxjs';
import { ApiError } from '../../core/services/api-error';
import { BarChartComponent, BarPoint } from '../../shared/components/bar-chart.component';
import { SolesPipe } from '../../shared/pipes/soles.pipe';
import { AdminApi, AdminOverview } from './admin.api';

type State = { status: 'loading' } | { status: 'error'; message: string } | { status: 'ready'; data: AdminOverview };

@Component({
  selector: 'app-admin-overview-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, SolesPipe, BarChartComponent],
  template: `
    <p class="type-eyebrow">Plataforma</p>
    <h1 class="type-h1 mt-3">Resumen</h1>
    <p class="mt-3 max-w-2xl">Solo dinero que los vendedores pagan por usar la plataforma. Las ventas de cada negocio a sus compradores no se suman aquí.</p>

    @let s = state();
    @if (s.status === 'loading') {
      <div class="mt-10 h-56 animate-pulse rounded bg-mist/25" aria-busy="true"><span class="sr-only">Cargando…</span></div>
    } @else if (s.status === 'error') {
      <p class="notice notice-error mt-8" role="alert">{{ s.message }}</p>
    } @else {
      @let d = s.data;
      @if (d.paymentsToVerify > 0) {
        <a routerLink="/admin/pagos" class="mt-8 flex items-center justify-between gap-4 rounded-[var(--radius-panel)] border border-warning/40 bg-warning-soft px-5 py-4 text-warning no-underline">
          <span><strong class="type-numeric">{{ d.paymentsToVerify }}</strong> {{ d.paymentsToVerify === 1 ? 'pago SaaS espera' : 'pagos SaaS esperan' }} verificación.</span>
          <span class="shrink-0 font-semibold">Revisar →</span>
        </a>
      }

      <div class="mt-10 grid gap-10 border-b border-[var(--line-subtle)] pb-10 lg:grid-cols-[1fr_2fr]">
        <div>
          <p class="text-sm font-semibold text-[var(--text-muted)]">Ingresos SaaS confirmados este mes</p>
          <p class="type-numeric mt-2 font-[family-name:var(--font-display)] text-5xl leading-none text-night">{{ d.revenueMonth | soles }}</p>
          <p class="mt-3 text-sm">Total histórico: <span class="type-numeric font-semibold">{{ d.revenueTotal | soles }}</span> · solo pagos verificados</p>
        </div>
        <div>
          <p class="text-sm font-semibold text-[var(--text-muted)]">Últimos 12 meses</p>
          <app-bar-chart class="mt-3 block" [points]="points()" caption="Ingresos SaaS confirmados por mes" />
        </div>
      </div>

      <dl class="mt-10 grid grid-cols-2 gap-x-8 gap-y-8 md:grid-cols-4">
        @for (m of metrics(); track m.label) {
          <div class="border-t border-[var(--line-subtle)] pt-4">
            <dt class="text-sm text-[var(--text-muted)]">{{ m.label }}</dt>
            <dd class="type-numeric mt-1 font-[family-name:var(--font-editorial)] text-3xl text-night">
              @if (m.link) { <a [routerLink]="'/admin/negocios'" [queryParams]="{ filtro: m.link }" class="no-underline hover:underline">{{ m.value }}</a> } @else { {{ m.value }} }
            </dd>
          </div>
        }
      </dl>

      <section class="mt-14" aria-labelledby="planes-contratados">
        <h2 id="planes-contratados" class="type-h2">Planes contratados</h2>
        @if (d.plans.length === 0) {
          <p class="mt-4">Aún no hay planes. <a class="link" routerLink="/admin/planes">Crea el primero</a>.</p>
        } @else {
          <ul class="mt-6 divide-y divide-[var(--line-subtle)] border-y border-[var(--line-subtle)]">
            @for (p of d.plans; track p.planId) {
              <li class="flex items-center justify-between py-3"><span class="font-semibold text-night">{{ p.planName }}</span>
                <span class="type-numeric">{{ p.businesses }} {{ p.businesses === 1 ? 'negocio' : 'negocios' }}</span></li>
            }
          </ul>
        }
      </section>
    }
  `,
})
export class AdminOverviewPage {
  protected readonly state = toSignal(
    inject(AdminApi).overview().pipe(
      map((data): State => ({ status: 'ready', data })),
      catchError((e: unknown) => of<State>({ status: 'error', message: ApiError.from(e).message })),
      startWith<State>({ status: 'loading' }),
    ),
    { requireSync: true },
  );

  protected readonly points = computed<BarPoint[]>(() => {
    const s = this.state();
    if (s.status !== 'ready') return [];
    const last = s.data.last12Months.length - 1;
    return s.data.last12Months.map((m, i) => ({ label: m.label, tick: m.label.split(' ')[0]!, value: m.amount, highlight: i === last }));
  });

  protected readonly metrics = computed(() => {
    const s = this.state();
    if (s.status !== 'ready') return [];
    const d = s.data;
    return [
      { label: 'Vendedores registrados', value: d.businessesTotal, link: 'ALL' },
      { label: 'Con plan vigente', value: d.businessesActive, link: 'ACTIVE' },
      { label: 'Pagos pendientes', value: d.subscriptionsPendingPayment, link: 'PENDING_PAYMENT' },
      { label: 'Sin plan', value: d.businessesNoPlan, link: 'NO_PLAN' },
      { label: 'En plazo de gracia', value: d.subscriptionsPastDue, link: 'PAST_DUE' },
      { label: 'Planes vencidos', value: d.subscriptionsExpired, link: 'EXPIRED' },
      { label: 'Negocios suspendidos', value: d.businessesSuspended, link: 'SUSPENDED' },
      { label: 'Nuevos en 30 días', value: d.newBusinesses30Days, link: null },
    ];
  });
}
