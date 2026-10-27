import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { catchError, combineLatest, debounceTime, distinctUntilChanged, map, of, startWith, switchMap } from 'rxjs';
import { ApiError } from '../../core/services/api-error';
import { Page } from '../../core/services/page';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { DurationPipe, SolesPipe } from '../../shared/pipes/soles.pipe';
import { FILTERS, STATUS_LABELS, availabilityLabel, typeLabel } from './product-labels';
import { Product, ProductFilter, ProductsApi } from './products.api';

type State = { status: 'loading' } | { status: 'error'; message: string } | { status: 'ready'; page: Page<Product> };

const PAGE_SIZE = 20;

@Component({
  selector: 'app-products-list-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, StatusBadgeComponent, SolesPipe, DurationPipe],
  templateUrl: './products-list.page.html',
})
export class ProductsListPage {
  private readonly api = inject(ProductsApi);

  protected readonly filters = FILTERS;
  protected readonly statusLabels = STATUS_LABELS;
  protected readonly typeLabel = typeLabel;
  protected readonly availabilityLabel = availabilityLabel;

  protected readonly search = new FormControl('', { nonNullable: true });
  protected readonly filter = signal<ProductFilter>('ACTIVE');
  protected readonly page = signal(0);
  protected readonly state = signal<State>({ status: 'loading' });

  constructor() {
    const query$ = this.search.valueChanges.pipe(
      startWith(''),
      debounceTime(300),
      map((q) => q.trim()),
      distinctUntilChanged(),
    );
    // Cambiar la búsqueda o el filtro vuelve a la primera página.
    query$.pipe(takeUntilDestroyed()).subscribe(() => this.page.set(0));

    combineLatest([query$, toObservable(this.filter), toObservable(this.page)])
      .pipe(
        debounceTime(0),
        switchMap(([query, filter, page]) =>
          this.api.list({ query, filter, page, size: PAGE_SIZE }).pipe(
            map((result): State => ({ status: 'ready', page: result })),
            catchError((e: unknown) => of<State>({ status: 'error', message: ApiError.from(e).message })),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((state) => this.state.set(state));
  }

  protected setFilter(filter: ProductFilter): void {
    this.filter.set(filter);
    this.page.set(0);
  }
}
