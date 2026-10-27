import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ApiError } from '../../core/services/api-error';
import { Page } from '../../core/services/page';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { formatDate } from '../billing/billing.api';
import { AdminApi, AdminBusinessSummary, BusinessFilter, SUBSCRIPTION_LABEL, subscriptionTone } from './admin.api';

const FILTERS: { value: BusinessFilter; label: string }[] = [
  { value: 'ALL', label: 'Todos' },
  { value: 'ACTIVE', label: 'Con plan vigente' },
  { value: 'PENDING_PAYMENT', label: 'Pago pendiente' },
  { value: 'PAST_DUE', label: 'En gracia' },
  { value: 'EXPIRED', label: 'Vencidos' },
  { value: 'NO_PLAN', label: 'Sin plan' },
  { value: 'SUSPENDED', label: 'Suspendidos' },
];

@Component({
  selector: 'app-admin-businesses-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, StatusBadgeComponent],
  template: `
    <p class="type-eyebrow">Vendedores</p>
    <h1 class="type-h1 mt-3">Negocios</h1>
    <p class="mt-3 max-w-2xl">Datos del vendedor y de su plan. Los compradores y las ventas de cada negocio no se muestran aquí.</p>

    <div class="mt-8 grid gap-4 md:grid-cols-[1fr_auto] md:items-end">
      <div class="field">
        <label class="field-label" for="q">Buscar</label>
        <input id="q" class="input" type="search" [formControl]="query" placeholder="Nombre, enlace, correo o código" />
      </div>
    </div>
    <nav class="mt-4 flex gap-1 overflow-x-auto border-b border-[var(--line-subtle)] pb-4" aria-label="Filtro">
      @for (f of filters; track f.value) {
        <button type="button" class="whitespace-nowrap rounded-full px-4 py-2 text-sm font-semibold"
                [class]="filter() === f.value ? 'bg-night text-snow' : 'text-corporate hover:bg-mist/30'" [attr.aria-pressed]="filter() === f.value" (click)="setFilter(f.value)">{{ f.label }}</button>
      }
    </nav>

    @if (error(); as e) { <p class="notice notice-error mt-6" role="alert">{{ e }}</p> }

    @if (page(); as p) {
      @if (p.items.length === 0) {
        <p class="py-16 text-center font-[family-name:var(--font-editorial)] text-2xl text-night">No hay negocios en este grupo.</p>
      } @else {
        <p class="mt-4 text-sm text-[var(--text-muted)]">{{ p.totalItems }} {{ p.totalItems === 1 ? 'negocio' : 'negocios' }}</p>
        <ul class="mt-2 divide-y divide-[var(--line-subtle)]">
          @for (b of p.items; track b.id) {
            <li class="grid gap-2 py-5 sm:grid-cols-[1.4fr_1fr_auto] sm:items-center sm:gap-6">
              <div class="min-w-0">
                <a [routerLink]="['/admin/negocios', b.id]" class="font-semibold text-night no-underline hover:underline">{{ b.businessName }}</a>
                <p class="truncate text-sm text-[var(--text-muted)]">{{ b.ownerName }} · {{ b.ownerEmail }}</p>
              </div>
              <div class="text-sm">
                <p class="font-semibold text-night">{{ b.planName ?? 'Sin plan' }}</p>
                @if (b.expiresAt) { <p class="text-[var(--text-muted)]">Vence el {{ formatDate(b.expiresAt) }}</p> }
              </div>
              <div class="flex flex-col items-start gap-1 sm:items-end">
                @if (b.status === 'SUSPENDED') { <app-status-badge label="Suspendido" tone="danger" /> }
                <app-status-badge [label]="b.subscriptionStatus ? subscriptionLabel[b.subscriptionStatus] : 'Sin plan'" [tone]="tone(b)" />
              </div>
            </li>
          }
        </ul>
        @if (p.totalPages > 1) {
          <div class="mt-6 flex items-center justify-between text-sm">
            <button type="button" class="btn btn-secondary !min-h-10" [disabled]="p.page === 0" (click)="load(p.page - 1)">Anterior</button>
            <span>Página {{ p.page + 1 }} de {{ p.totalPages }}</span>
            <button type="button" class="btn btn-secondary !min-h-10" [disabled]="p.page + 1 >= p.totalPages" (click)="load(p.page + 1)">Siguiente</button>
          </div>
        }
      }
    } @else if (!error()) {
      <div class="mt-6 h-40 animate-pulse rounded bg-mist/25" aria-busy="true"></div>
    }
  `,
})
export class AdminBusinessesPage {
  private readonly api = inject(AdminApi);
  private readonly router = inject(Router);
  protected readonly filters = FILTERS;
  protected readonly subscriptionLabel = SUBSCRIPTION_LABEL;
  protected readonly formatDate = formatDate;
  protected readonly query = new FormControl('', { nonNullable: true });
  protected readonly filter = signal<BusinessFilter>('ALL');
  protected readonly page = signal<Page<AdminBusinessSummary> | null>(null);
  protected readonly error = signal<string | null>(null);

  constructor() {
    const requested = inject(ActivatedRoute).snapshot.queryParamMap.get('filtro') as BusinessFilter | null;
    if (requested && FILTERS.some((f) => f.value === requested)) this.filter.set(requested);
    this.query.valueChanges.pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed()).subscribe(() => this.load(0));
    this.load(0);
  }

  protected setFilter(f: BusinessFilter): void {
    this.filter.set(f);
    void this.router.navigate([], { queryParams: { filtro: f === 'ALL' ? null : f }, replaceUrl: true });
    this.load(0);
  }

  protected load(page: number): void {
    this.error.set(null);
    this.api.businesses(this.query.value.trim(), this.filter(), page).subscribe({
      next: (p) => this.page.set(p),
      error: (e: unknown) => this.error.set(ApiError.from(e).message),
    });
  }

  protected tone(b: AdminBusinessSummary) {
    return subscriptionTone(b.subscriptionStatus);
  }
}
