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
    expect(exhausted.querySelector('a')).toBeNull();
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
});
