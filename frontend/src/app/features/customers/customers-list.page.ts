import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { catchError, combineLatest, debounceTime, distinctUntilChanged, map, of, startWith, switchMap } from 'rxjs';
import { ApiError } from '../../core/services/api-error';
import { Page } from '../../core/services/page';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { shortDate } from '../subscriptions/subscriptions.api';
import { CustomerFilter, CustomerSummary, CustomersApi } from './customers.api';

type State = { status: 'loading' } | { status: 'error'; message: string } | { status: 'ready'; page: Page<CustomerSummary> };

@Component({
  selector: 'app-customers-list-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, StatusBadgeComponent],
  template: `
    <section class="mx-auto max-w-6xl" aria-labelledby="clientes-titulo">
      <div class="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p class="type-eyebrow">Clientes</p>
          <h1 id="clientes-titulo" class="type-h1 mt-3">Tus clientes</h1>
        </div>
        <div class="flex flex-wrap gap-3">
          <a class="btn btn-secondary" routerLink="/panel/clientes/importar">Importar desde archivo</a>
          <a class="btn btn-primary" routerLink="/panel/clientes/nuevo">Registrar cliente</a>
        </div>
      </div>

      <div class="mt-8 flex flex-col gap-4 border-b border-[var(--line-subtle)] pb-5 md:flex-row md:items-center md:justify-between">
        <div class="flex gap-1 overflow-x-auto" role="group" aria-label="Filtrar clientes">
          @for (f of filters; track f.value) {
            <button type="button" class="whitespace-nowrap rounded-full px-4 py-2 text-sm font-semibold transition-colors"
                    [class]="filter() === f.value ? 'bg-night text-snow' : 'text-corporate hover:bg-mist/30'"
                    [attr.aria-pressed]="filter() === f.value" (click)="setFilter(f.value)">{{ f.label }}</button>
          }
        </div>
        <label class="md:w-80">
          <span class="sr-only">Buscar por nombre o celular</span>
          <input class="input" type="search" [formControl]="search" placeholder="Buscar por nombre o celular" />
        </label>
      </div>

      @let s = state();
      @if (s.status === 'loading') {
        <div class="mt-6 space-y-3" aria-busy="true">@for (i of [1, 2, 3]; track i) { <div class="h-14 animate-pulse rounded bg-mist/25"></div> }</div>
      } @else if (s.status === 'error') {
        <p class="notice notice-error mt-6" role="alert">{{ s.message }}</p>
      } @else if (s.page.totalItems === 0) {
        <div class="py-20 text-center">
          <p class="font-[family-name:var(--font-editorial)] text-2xl text-night">{{ search.value ? 'Sin resultados para esa búsqueda.' : 'Aún no hay clientes con este filtro.' }}</p>
          @if (filter() === 'ALL' && !search.value) {
            <p class="mt-3">Tus compradores aparecerán aquí. También puedes registrar a los clientes que ya tenías.</p>
          }
        </div>
      } @else {
        <ul class="divide-y divide-[var(--line-subtle)]">
          @for (c of s.page.items; track c.id) {
            <li>
              <a [routerLink]="['/panel/clientes', c.id]" class="grid gap-1 py-4 no-underline sm:grid-cols-[1.4fr_1fr_1fr] sm:items-center sm:gap-6">
                <div class="min-w-0">
                  <p class="truncate font-semibold text-night">{{ c.fullName }}</p>
                  <p class="type-numeric text-sm text-[var(--text-muted)]">{{ local(c.phone) }}{{ c.source === 'MANUAL' ? ' · Registrado a mano' : '' }}</p>
                </div>
                <div class="text-sm">
                  @if (c.activeSubscriptions > 0) {
                    <app-status-badge [label]="c.activeSubscriptions === 1 ? '1 suscripción activa' : c.activeSubscriptions + ' suscripciones activas'" tone="success" />
                  } @else if (c.hasExpired) {
                    <app-status-badge label="Suscripción vencida" tone="danger" />
                  } @else {
                    <span class="text-[var(--text-muted)]">Sin suscripción activa</span>
                  }
                </div>
                <p class="text-sm text-[var(--text-muted)]">{{ c.nextExpiry ? 'Próximo vencimiento: ' + date(c.nextExpiry) : '' }}</p>
              </a>
            </li>
          }
        </ul>
        @if (s.page.totalPages > 1) {
          <nav class="mt-6 flex items-center justify-between border-t border-[var(--line-subtle)] pt-5" aria-label="Paginación">
            <button type="button" class="btn btn-secondary !min-h-10" [disabled]="page() === 0" (click)="page.set(page() - 1)">Anterior</button>
            <span class="text-sm text-[var(--text-muted)]">Página {{ page() + 1 }} de {{ s.page.totalPages }}</span>
            <button type="button" class="btn btn-secondary !min-h-10" [disabled]="page() + 1 >= s.page.totalPages" (click)="page.set(page() + 1)">Siguiente</button>
          </nav>
        }
      }
    </section>
  `,
})
export class CustomersListPage {
  private readonly api = inject(CustomersApi);
  protected readonly date = shortDate;
  protected readonly filters: { value: CustomerFilter; label: string }[] = [
    { value: 'ALL', label: 'Todos' },
    { value: 'ACTIVE', label: 'Activos' },
    { value: 'EXPIRING', label: 'Por vencer' },
    { value: 'EXPIRED', label: 'Vencidos' },
  ];
  protected readonly search = new FormControl('', { nonNullable: true });
  protected readonly filter = signal<CustomerFilter>('ALL');
  protected readonly page = signal(0);
  protected readonly state = signal<State>({ status: 'loading' });

  constructor() {
    const query$ = this.search.valueChanges.pipe(startWith(''), debounceTime(300), map((q) => q.trim()), distinctUntilChanged());
    query$.pipe(takeUntilDestroyed()).subscribe(() => this.page.set(0));
    combineLatest([query$, toObservable(this.filter), toObservable(this.page)])
      .pipe(
        debounceTime(0),
        switchMap(([query, filter, page]) =>
          this.api.list(filter, query, page).pipe(
            map((result): State => ({ status: 'ready', page: result })),
            catchError((e: unknown) => of<State>({ status: 'error', message: ApiError.from(e).message })),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((s) => this.state.set(s));
  }

  protected setFilter(f: CustomerFilter): void {
    this.filter.set(f);
    this.page.set(0);
  }

  protected local(phone: string): string {
    return phone.replace(/^\+51/, '');
  }
}
