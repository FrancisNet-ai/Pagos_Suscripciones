import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { BRAND } from '../../core/brand';
import { ApiError } from '../../core/services/api-error';
import { storeTheme } from '../../shared/components/store-theme';
import { DurationPipe, SolesPipe } from '../../shared/pipes/soles.pipe';
import { PublicProduct, PublicStore, StorefrontApi } from './storefront.api';

@Component({
  selector: 'app-storefront-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, SolesPipe, DurationPipe],
  templateUrl: './storefront.page.html',
})
export class StorefrontPage implements OnInit {
  readonly slug = input.required<string>();

  private readonly api = inject(StorefrontApi);
  private readonly title = inject(Title);
  protected readonly brand = BRAND;
  protected readonly store = signal<PublicStore | null>(null);
  protected readonly products = signal<PublicProduct[]>([]);
  protected readonly error = signal<{ status: number; message: string } | null>(null);
  protected readonly theme = computed(() => storeTheme(this.store()?.primaryColor));
  protected readonly initials = computed(() =>
    (this.store()?.businessName ?? '').split(/\s+/).filter(Boolean).slice(0, 2).map((w) => w[0]!.toUpperCase()).join(''),
  );

  ngOnInit(): void {
    forkJoin({ store: this.api.store(this.slug()), products: this.api.products(this.slug()) }).subscribe({
      next: ({ store, products }) => {
        this.store.set(store);
        this.products.set(products);
        this.title.setTitle(store.businessName);
      },
      error: (e: unknown) => {
        const err = ApiError.from(e);
        this.error.set({ status: err.status, message: err.message });
      },
    });
  }

  protected availabilityText(p: PublicProduct): string | null {
    if (p.soldOut) return 'Agotado — Actualmente no hay cupos disponibles.';
    if (!p.purchasable) return 'No disponible por ahora.';
    if (p.availableSlots !== null) return p.availableSlots === 1 ? 'Queda 1 cupo' : `Quedan ${p.availableSlots} cupos`;
    return null;
  }
}
