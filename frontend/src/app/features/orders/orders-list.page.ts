import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { catchError, combineLatest, debounceTime, distinctUntilChanged, map, of, startWith, switchMap } from 'rxjs';
import { ApiError } from '../../core/services/api-error';
import { Page } from '../../core/services/page';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { SolesPipe } from '../../shared/pipes/soles.pipe';
import { METHOD_LABEL, ORDER_FILTERS, OrderFilter, OrderSummary, OrdersApi, formatDateTime, orderTone } from './orders.api';

type State = { status: 'loading' } | { status: 'error'; message: string } | { status: 'ready'; page: Page<OrderSummary> };

@Component({
  selector: 'app-orders-list-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, StatusBadgeComponent, SolesPipe],
  templateUrl: './orders-list.page.html',
})
export class OrdersListPage {
  /** Query param `filtro` (p. ej. desde el menú «Por verificar»). */
  readonly filtro = input<string>();

  private readonly api = inject(OrdersApi);
  protected readonly filters = ORDER_FILTERS;
  protected readonly methodLabel = METHOD_LABEL;
  protected readonly tone = orderTone;
  protected readonly date = formatDateTime;

  protected readonly search = new FormControl('', { nonNullable: true });
  protected readonly from = new FormControl('', { nonNullable: true });
  protected readonly to = new FormControl('', { nonNullable: true });
  protected readonly filter = signal<OrderFilter>('ALL');
  protected readonly page = signal(0);
  protected readonly state = signal<State>({ status: 'loading' });

  constructor() {
    toObservable(this.filtro)
      .pipe(takeUntilDestroyed())
      .subscribe((f) => {
        if (f && ORDER_FILTERS.some((x) => x.value === f)) this.setFilter(f as OrderFilter);
      });

    const text$ = this.search.valueChanges.pipe(startWith(''), debounceTime(300), map((q) => q.trim()), distinctUntilChanged());
    const from$ = this.from.valueChanges.pipe(startWith(''));
    const to$ = this.to.valueChanges.pipe(startWith(''));
    combineLatest([text$, from$, to$]).pipe(takeUntilDestroyed()).subscribe(() => this.page.set(0));

    combineLatest([text$, from$, to$, toObservable(this.filter), toObservable(this.page)])
      .pipe(
        debounceTime(0),
        switchMap(([query, from, to, filter, page]) =>
          this.api.list({ filter, query, from, to, page }).pipe(
            map((result): State => ({ status: 'ready', page: result })),
            catchError((e: unknown) => of<State>({ status: 'error', message: ApiError.from(e).message })),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((s) => this.state.set(s));
  }

  protected setFilter(filter: OrderFilter): void {
    this.filter.set(filter);
    this.page.set(0);
  }

  protected localPhone(e164: string): string {
    return e164.replace(/^\+51/, '');
  }
}
