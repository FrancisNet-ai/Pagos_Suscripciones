import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { CheckoutPage } from './checkout.page';

const store = {
  slug: 'mi-tienda', businessName: 'Mi Tienda', description: null, logoUrl: null, coverUrl: null, primaryColor: '#1A3D63',
  acceptingOrders: true, notice: null, paymentMethods: [{ method: 'YAPE', label: 'Yape' }, { method: 'PLIN', label: 'Plin' }],
};
const product = {
  id: 'p1', name: 'Servicio Premium', description: null, category: null, productType: 'SUBSCRIPTION', price: 15, currency: 'PEN',
  durationDays: 30, imageUrl: null, availableSlots: null, soldOut: false, purchasable: true,
};

describe('CheckoutPage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [CheckoutPage], providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function render(p: object = product) {
    const fixture = TestBed.createComponent(CheckoutPage);
    fixture.componentRef.setInput('slug', 'mi-tienda');
    fixture.componentRef.setInput('productId', 'p1');
    fixture.detectChanges();
    http.expectOne('/api/v1/public/stores/mi-tienda').flush(store);
    http.expectOne('/api/v1/public/stores/mi-tienda/products/p1').flush(p);
    await fixture.whenStable();
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  function type(el: HTMLElement, selector: string, value: string) {
    const input = el.querySelector<HTMLInputElement>(selector)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  it('exige datos, método de pago y consentimientos sin llamar a la API', async () => {
    const { fixture, el } = await render();
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    fixture.detectChanges();
    expect(el.querySelectorAll('.field-error').length).toBe(5);
  });

  it('envía solo producto y datos de contacto (nunca el precio) con clave de idempotencia', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const { fixture, el } = await render();
    type(el, '#customerName', 'Carlos Ramírez');
    type(el, '#phone', '987654321');
    Array.from(el.querySelectorAll('label')).find((l) => l.textContent?.trim() === 'Plin')!.querySelector('input')!.click();
    el.querySelector<HTMLInputElement>('input[formcontrolname=acceptTerms]')!.click();
    el.querySelector<HTMLInputElement>('input[formcontrolname=acceptDataProcessing]')!.click();
    fixture.detectChanges();
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();

    const req = http.expectOne('/api/v1/public/stores/mi-tienda/orders');
    expect(req.request.body).toEqual({
      productId: 'p1', customerName: 'Carlos Ramírez', phone: '987654321', paymentMethod: 'PLIN',
      acceptTerms: true, acceptDataProcessing: true, acceptRenewalReminders: false,
    });
    expect(req.request.body.price).toBeUndefined();
    expect(req.request.headers.get('Idempotency-Key')).toMatch(/^[0-9a-f-]{36}$/);
    req.flush({ publicToken: 'tok', orderNumber: 'ORD-2026-000001' });
    expect(navigate).toHaveBeenCalledWith(['/pedido', 'tok'], { replaceUrl: true });
  });

  it('un producto agotado no muestra el formulario', async () => {
    const { el } = await render({ ...product, soldOut: true, purchasable: false });
    expect(el.textContent).toContain('Agotado — Actualmente no hay cupos disponibles.');
    expect(el.querySelector('form')).toBeNull();
  });
});
