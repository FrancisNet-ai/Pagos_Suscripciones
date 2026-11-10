import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ApiError } from '../../core/services/api-error';
import { IconComponent } from '../../shared/components/icon.component';
import { LoadingIndicatorComponent } from '../../shared/components/loading-indicator.component';
import { storeTheme } from '../../shared/components/store-theme';
import { RichTextPipe } from '../../shared/pipes/rich-text.pipe';
import { SmallImagePipe } from '../../shared/pipes/small-image.pipe';
import { DurationPipe, SolesPipe } from '../../shared/pipes/soles.pipe';
import { PublicProduct, PublicStore, StorefrontApi } from './storefront.api';

/**
 * Detalle de un producto de la tienda: imagen grande, descripción completa con sus beneficios, precio, cupos y
 * el botón para comprar (lleva al mismo formulario de pedido que la tarjeta del catálogo).
 */
@Component({
  selector: 'app-product-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LoadingIndicatorComponent, RouterLink, SmallImagePipe, SolesPipe, DurationPipe, RichTextPipe, IconComponent],
  templateUrl: './product.page.html',
})
export class ProductPage implements OnInit {
  readonly slug = input.required<string>();
  readonly productId = input.required<string>();

  private readonly api = inject(StorefrontApi);
  private readonly title = inject(Title);
  protected readonly store = signal<PublicStore | null>(null);
  protected readonly product = signal<PublicProduct | null>(null);
  protected readonly error = signal<{ status: number; message: string } | null>(null);
  protected readonly theme = computed(() => storeTheme(this.store()?.primaryColor));
  protected readonly canBuy = computed(() => !!this.product()?.purchasable && !!this.store()?.acceptingOrders);
  protected readonly payWith = computed(() => {
    const methods = this.store()?.paymentMethods ?? [];
    return methods.length === 2 ? 'Yape o Plin' : (methods[0]?.label ?? null);
  });

  ngOnInit(): void {
    forkJoin({ store: this.api.store(this.slug()), product: this.api.product(this.slug(), this.productId()) }).subscribe({
      next: ({ store, product }) => {
        this.store.set(store);
        this.product.set(product);
        this.title.setTitle(`${product.name} · ${store.businessName}`);
      },
      error: (e: unknown) => {
        const err = ApiError.from(e);
        this.error.set({ status: err.status, message: err.message });
      },
    });
  }

  protected availability(p: PublicProduct): string | null {
    if (p.soldOut) return 'Agotado: por ahora no hay cupos disponibles.';
    if (!p.purchasable) return 'No disponible por ahora.';
    if (p.availableSlots !== null) return p.availableSlots === 1 ? 'Queda 1 cupo' : `Quedan ${p.availableSlots} cupos`;
    return null;
  }
}
