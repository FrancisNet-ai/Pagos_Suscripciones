import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ProductPage } from './product.page';

const store = {
  slug: 'tecno', businessName: 'Tienda Tecno', description: null, logoUrl: null, coverUrl: null, primaryColor: null,
  acceptingOrders: true, notice: null, paymentMethods: [{ method: 'YAPE', label: 'Yape' }, { method: 'PLIN', label: 'Plin' }],
  orderUpdatesByWhatsapp: false,
};
const product = {
  id: 'p1', name: 'Crunchyroll Mega Fan', description: '**Crunchyroll Premium** 🍥\nAnimes sin anuncios', category: 'Entretenimiento',
  productType: 'SUBSCRIPTION', price: 4.8, currency: 'PEN', durationDays: 30, imageUrl: null, availableSlots: 5, soldOut: false, purchasable: true,
};

describe('ProductPage', () => {
  function render(p: object = product) {
    TestBed.configureTestingModule({ imports: [ProductPage], providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(ProductPage);
    fixture.componentRef.setInput('slug', 'tecno');
    fixture.componentRef.setInput('productId', 'p1');
    fixture.detectChanges();
    http.expectOne('/api/v1/public/stores/tecno').flush(store);
    http.expectOne('/api/v1/public/stores/tecno/products/p1').flush(p);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('muestra la descripción completa con formato, el precio, los cupos y el botón para comprar', () => {
    const el = render();
    expect(el.querySelector('h1')?.textContent).toContain('Crunchyroll Mega Fan');
    const description = el.querySelector('.product-detail__description')!;
    expect(description.querySelector('strong')?.textContent).toBe('Crunchyroll Premium');
    expect(description.innerHTML).toContain('<br>');
    expect(el.textContent).toContain('Quedan 5 cupos');
    expect(el.textContent).toContain('Pagas por Yape o Plin directo al vendedor');
    const buy = el.querySelector<HTMLAnchorElement>('.product-detail__buy')!;
    expect(buy.getAttribute('href')).toBe('/tienda/tecno/comprar/p1');
  });

  it('un producto agotado no ofrece comprar', () => {
    const el = render({ ...product, soldOut: true, purchasable: false, availableSlots: 0 });
    expect(el.querySelector('.product-detail__buy')).toBeNull();
    expect(el.querySelector('.product-buybar')).toBeNull();
    expect(el.textContent).toContain('Agotado');
  });
});
