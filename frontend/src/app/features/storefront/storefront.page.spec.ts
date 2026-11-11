import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { StorefrontPage } from './storefront.page';
import { PublicProduct } from './storefront.api';

const products: PublicProduct[] = [
  {
    id: 'p1',
    name: 'Curso digital',
    description: 'Aprende',
    category: 'Educación',
    productType: 'SUBSCRIPTION',
    price: 15,
    currency: 'PEN',
    durationDays: 30,
    imageUrl: null,
    availableSlots: 2,
    soldOut: false,
    purchasable: true,
  },
  {
    id: 'p2',
    name: 'Diseño Pro',
    description: null,
    category: 'Software',
    productType: 'ONE_TIME',
    price: 39,
    currency: 'PEN',
    durationDays: null,
    imageUrl: null,
    availableSlots: null,
    soldOut: false,
    purchasable: true,
  },
  {
    id: 'p3',
    name: 'Curso agotado',
    description: null,
    category: 'Educación',
    productType: 'SUBSCRIPTION',
    price: 19,
    currency: 'PEN',
    durationDays: 30,
    imageUrl: null,
    availableSlots: 0,
    soldOut: true,
    purchasable: false,
  },
];

describe('StorefrontPage: catálogo visual', () => {
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [StorefrontPage],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  function render() {
    const fixture = TestBed.createComponent(StorefrontPage);
    fixture.componentRef.setInput('slug', 'tienda');
    fixture.detectChanges();
    http.expectOne('/api/v1/public/stores/tienda').flush({
      slug: 'tienda',
      businessName: 'Negocio real',
      description: null,
      logoUrl: null,
      coverUrl: null,
      primaryColor: '#1A3D63',
      acceptingOrders: true,
      notice: null,
      paymentMethods: [{ method: 'YAPE', label: 'Yape' }],
    });
    http.expectOne('/api/v1/public/stores/tienda/products').flush(products);
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('mantiene el monograma hasta recibir la tienda y el catálogo', () => {
    const fixture = TestBed.createComponent(StorefrontPage);
    fixture.componentRef.setInput('slug', 'tienda');
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('app-loading-indicator')?.getAttribute('aria-label')).toBe('Cargando tienda…');
    expect(el.querySelector('app-loading-indicator')?.textContent?.trim()).toBe('');
    http.expectOne('/api/v1/public/stores/tienda/products').flush(products);
    fixture.detectChanges();
    expect(el.querySelector('app-loading-indicator')).not.toBeNull();
    http.expectOne('/api/v1/public/stores/tienda').flush({ businessName: 'Negocio real', paymentMethods: [] });
    fixture.detectChanges();
    expect(el.querySelector('app-loading-indicator')).toBeNull();
    expect(el.querySelector('.store-nav')?.textContent).toContain('Negocio real');
  });

  it('sustituye el monograma por el error si no puede cargar la tienda', () => {
    const fixture = TestBed.createComponent(StorefrontPage);
    fixture.componentRef.setInput('slug', 'tienda');
    fixture.detectChanges();
    const catalog = http.expectOne('/api/v1/public/stores/tienda/products');
    http.expectOne('/api/v1/public/stores/tienda').flush({}, { status: 503, statusText: 'Service Unavailable' });
    fixture.detectChanges();
    expect(catalog.cancelled).toBe(true);
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('app-loading-indicator')).toBeNull();
    expect(el.textContent).toContain('No pudimos cargar la tienda');
  });

  it('filtra categorías y búsqueda a partir de productos reales', () => {
    const { fixture, el } = render();
    const category = Array.from(el.querySelectorAll('button')).find(
      (button) => button.textContent?.trim() === 'Educación',
    )!;
    category.click();
    fixture.detectChanges();
    expect(el.querySelectorAll('.store-product').length).toBe(2);
    const search = el.querySelector<HTMLInputElement>('input[type=search]')!;
    search.value = 'digital';
    search.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(el.querySelectorAll('.store-product').length).toBe(1);
    expect(el.querySelector('.store-product')!.textContent).toContain('Curso digital');
  });

  it('mantiene visibles los agotados y no ofrece comprarlos', () => {
    const { el } = render();
    const exhausted = Array.from(el.querySelectorAll('.store-product')).find((product) =>
      product.textContent?.includes('Curso agotado'),
    )!;
    expect(exhausted.textContent).toContain('Agotado');
    // Se puede ver su detalle, pero no comprarlo.
    expect(exhausted.querySelector('a.btn-store')).toBeNull();
    expect(exhausted.querySelector('a.store-product-link')).not.toBeNull();
    expect(el.querySelector('.store-nav')!.textContent).toContain('Negocio real');
  });

  it('permite recuperar el catálogo después de una búsqueda sin resultados', () => {
    const { fixture, el } = render();
    const search = el.querySelector<HTMLInputElement>('input[type=search]')!;
    search.value = 'no-existe';
    search.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(el.textContent).toContain('No encontramos productos con esos filtros.');
    Array.from(el.querySelectorAll('button'))
      .find((button) => button.textContent?.trim() === 'Mostrar todos')!
      .click();
    fixture.detectChanges();
    expect(search.value).toBe('');
    expect(el.querySelectorAll('.store-product').length).toBe(3);
  });
  it('cada tarjeta abre el detalle del producto y muestra la descripción sin marcas de formato', () => {
    const fixture = TestBed.createComponent(StorefrontPage);
    fixture.componentRef.setInput('slug', 'demo');
    fixture.detectChanges();
    http.expectOne('/api/v1/public/stores/demo').flush({
      slug: 'demo', businessName: 'Demo', description: '**Tienda Demo**: todo digital', logoUrl: null, coverUrl: null, primaryColor: null,
      acceptingOrders: true, notice: null, paymentMethods: [], orderUpdatesByWhatsapp: false,
    });
    http.expectOne('/api/v1/public/stores/demo/products').flush([{
      id: 'p9', name: 'Prime Video', description: '**Prime Video** 🎬\nSeries', category: null, productType: 'SUBSCRIPTION',
      price: 5.2, currency: 'PEN', durationDays: 30, imageUrl: null, availableSlots: null, soldOut: false, purchasable: true,
    }]);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.store-product-link')?.getAttribute('href')).toBe('/tienda/demo/producto/p9');
    expect(el.querySelector('.product-description')?.textContent?.trim()).toBe('Prime Video 🎬 Series');
    expect(el.querySelector('.store-banner-inner strong')?.textContent).toBe('Tienda Demo');
  });
  it('la portada muestra solo la frase principal y el resto se abre con «Ver más sobre la tienda»', () => {
    const fixture = TestBed.createComponent(StorefrontPage);
    fixture.componentRef.setInput('slug', 'larga');
    fixture.detectChanges();
    http.expectOne('/api/v1/public/stores/larga').flush({
      slug: 'larga', businessName: 'Larga', description: '**TecnoSiam** | Soluciones digitales\n\nSoporte técnico\n- Formateo de laptops',
      logoUrl: null, coverUrl: null, primaryColor: null, acceptingOrders: true, notice: null, paymentMethods: [], orderUpdatesByWhatsapp: false,
    });
    http.expectOne('/api/v1/public/stores/larga/products').flush([]);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.store-tagline')?.textContent).toBe('TecnoSiam | Soluciones digitales');
    expect(el.textContent).not.toContain('Formateo de laptops');
    const toggle = el.querySelector<HTMLButtonElement>('.store-about-toggle')!;
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    toggle.click();
    fixture.detectChanges();
    expect(toggle.getAttribute('aria-expanded')).toBe('true');
    expect(el.querySelector('#sobre-la-tienda')?.textContent).toContain('Formateo de laptops');
  });
});
