import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { catchError, combineLatest, debounceTime, distinctUntilChanged, map, of, startWith, switchMap } from 'rxjs';
import { ApiError } from '../../core/services/api-error';
import { Page } from '../../core/services/page';
import { SubscriptionItemComponent } from './subscription-item.component';
import { Subscription, SubscriptionFilter, SubscriptionsApi } from './subscriptions.api';

type State = { status: 'loading' } | { status: 'error'; message: string } | { status: 'ready'; page: Page<Subscription> };

const FILTERS: { value: SubscriptionFilter; label: string; empty: string }[] = [
  { value: 'PENDING_ACTIVATION', label: 'Por activar', empty: 'No hay suscripciones pagadas pendientes de activar.' },
  { value: 'EXPIRING', label: 'Por vencer', empty: 'Ninguna suscripción vence en los próximos 7 días.' },
  { value: 'ACTIVE', label: 'Activas', empty: 'No hay suscripciones activas.' },
  { value: 'EXPIRED', label: 'Vencidas', empty: 'No hay suscripciones vencidas.' },
  { value: 'CLOSED', label: 'Finalizadas', empty: 'No hay suscripciones finalizadas o canceladas.' },
  { value: 'ALL', label: 'Todas', empty: 'Aún no hay suscripciones.' },
];

@Component({
  selector: 'app-subscriptions-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, SubscriptionItemComponent],
  template: `
    <section class="mx-auto max-w-5xl" aria-labelledby="subs-titulo">
      <p class="type-eyebrow">Suscripciones y renovaciones</p>
      <h1 id="subs-titulo" class="type-h1 mt-3">Suscripciones</h1>
      <p class="mt-3 max-w-2xl">Activa las suscripciones pagadas cuando entregues el servicio y controla sus vencimientos. Un cupo solo se libera cuando confirmas que retiraste el acceso.</p>

      <div class="mt-8 flex flex-col gap-4 border-b border-[var(--line-subtle)] pb-5 md:flex-row md:items-center md:justify-between">
        <div class="flex gap-1 overflow-x-auto" role="group" aria-label="Filtrar suscripciones">
          @for (f of filters; track f.value) {
            <button type="button" class="whitespace-nowrap rounded-full px-4 py-2 text-sm font-semibold transition-colors"
                    [class]="filter() === f.value ? 'bg-night text-snow' : 'text-corporate hover:bg-mist/30'"
                    [attr.aria-pressed]="filter() === f.value" (click)="setFilter(f.value)">{{ f.label }}</button>
          }
        </div>
        <label class="md:w-72">
          <span class="sr-only">Buscar por cliente, celular o producto</span>
          <input class="input" type="search" [formControl]="search" placeholder="Cliente, celular o producto" />
        </label>
      </div>

      @let s = state();
      @if (s.status === 'loading') {
        <div class="mt-6 space-y-3" aria-busy="true">@for (i of [1, 2, 3]; track i) { <div class="h-20 animate-pulse rounded bg-mist/25"></div> }</div>
      } @else if (s.status === 'error') {
        <p class="notice notice-error mt-6" role="alert">{{ s.message }}</p>
      } @else if (s.page.totalItems === 0) {
        <p class="py-20 text-center font-[family-name:var(--font-editorial)] text-2xl text-night">{{ emptyText() }}</p>
      } @else {
        <div class="divide-y divide-[var(--line-subtle)]">
          @for (sub of s.page.items; track sub.id) {
            <app-subscription-item [sub]="sub" [showCustomer]="true" (changed)="reload()" />
          }
        </div>
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
export class SubscriptionsPage {
  /** Query param `filtro`. */
  readonly filtro = input<string>();

  private readonly api = inject(SubscriptionsApi);
  protected readonly filters = FILTERS;
  protected readonly search = new FormControl('', { nonNullable: true });
  protected readonly filter = signal<SubscriptionFilter>('PENDING_ACTIVATION');
  protected readonly page = signal(0);
  private readonly refresh = signal(0);
  protected readonly state = signal<State>({ status: 'loading' });

  constructor() {
    toObservable(this.filtro).pipe(takeUntilDestroyed()).subscribe((f) => {
      if (f && FILTERS.some((x) => x.value === f)) this.setFilter(f as SubscriptionFilter);
    });
    const query$ = this.search.valueChanges.pipe(startWith(''), debounceTime(300), map((q) => q.trim()), distinctUntilChanged());
    query$.pipe(takeUntilDestroyed()).subscribe(() => this.page.set(0));
    combineLatest([query$, toObservable(this.filter), toObservable(this.page), toObservable(this.refresh)])
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

  protected setFilter(f: SubscriptionFilter): void {
    this.filter.set(f);
    this.page.set(0);
  }

  /** Tras una acción, la suscripción puede haber cambiado de grupo: se recarga la lista. */
  protected reload(): void {
    this.refresh.update((n) => n + 1);
  }

  protected emptyText(): string {
    return FILTERS.find((f) => f.value === this.filter())!.empty;
  }
}
