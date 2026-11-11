import { SocialLinksComponent } from '../../shared/components/social-links.component';
import { LoadingIndicatorComponent } from '../../shared/components/loading-indicator.component';
import { PlainTextPipe, RichTextPipe } from '../../shared/pipes/rich-text.pipe';
import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { IconComponent } from '../../shared/components/icon.component';
import { WaveArtComponent } from '../../shared/components/wave-art.component';
import { BRAND } from '../../core/brand';
import { ApiError } from '../../core/services/api-error';
import { storeTheme } from '../../shared/components/store-theme';
import { DurationPipe, SolesPipe } from '../../shared/pipes/soles.pipe';
import { PublicProduct, PublicStore, StorefrontApi } from './storefront.api';
import { SmallImagePipe } from '../../shared/pipes/small-image.pipe';

/** Más que esto no cabe en dos líneas de la portada en celular: se ofrece «Ver más». */
const TAGLINE_MAX = 110;

@Component({
  selector: 'app-storefront-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LoadingIndicatorComponent, SmallImagePipe, RouterLink, SolesPipe, DurationPipe, IconComponent, WaveArtComponent, RichTextPipe, PlainTextPipe, SocialLinksComponent],
  templateUrl: './storefront.page.html',
})
export class StorefrontPage implements OnInit {
  readonly slug = input.required<string>();

  private readonly api = inject(StorefrontApi);
  private readonly title = inject(Title);
  protected readonly brand = BRAND;
  protected readonly store = signal<PublicStore | null>(null);
  protected readonly products = signal<PublicProduct[]>([]);
  protected readonly category = signal<string | null>(null);
  protected readonly search = signal('');
  protected readonly categories = computed(() =>
    [
      ...new Set(
        this.products()
          .map((p) => p.category)
          .filter((c): c is string => !!c),
      ),
    ].sort((a, b) => a.localeCompare(b, 'es')),
  );
  protected readonly filteredProducts = computed(() => {
    const query = this.search().trim().toLocaleLowerCase('es');
    return this.products().filter(
      (p) =>
        (this.category() === null || p.category === this.category()) &&
        (!query || `${p.name} ${p.category ?? ''}`.toLocaleLowerCase('es').includes(query)),
    );
  });
  protected setSearch(event: Event): void {
    this.search.set((event.target as HTMLInputElement).value);
  }
  protected clearFilters(): void {
    this.search.set('');
    this.category.set(null);
  }
  protected readonly error = signal<{ status: number; message: string } | null>(null);
  /** La descripción completa del vendedor se abre aparte para que el catálogo quede a la vista. */
  protected readonly aboutOpen = signal(false);
  private readonly descriptionLines = computed(() =>
    (this.store()?.description ?? '').split(/\r?\n/).map((l) => l.trim()).filter(Boolean),
  );
  /** Frase principal de la portada: la primera línea de la descripción. */
  protected readonly tagline = computed(() => this.descriptionLines()[0] ?? null);
  protected readonly hasMoreAbout = computed(() => {
    const lines = this.descriptionLines();
    return lines.length > 1 || (lines[0]?.length ?? 0) > TAGLINE_MAX;
  });
  protected readonly theme = computed(() => storeTheme(this.store()?.primaryColor));
  protected readonly initials = computed(() =>
    (this.store()?.businessName ?? '')
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((w) => w[0]!.toUpperCase())
      .join(''),
  );

  ngOnInit(): void {
    forkJoin({
      store: this.api.store(this.slug()),
      products: this.api.products(this.slug()),
    }).subscribe({
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
    if (p.availableSlots !== null)
      return p.availableSlots === 1 ? 'Queda 1 cupo' : `Quedan ${p.availableSlots} cupos`;
    return null;
  }
}
