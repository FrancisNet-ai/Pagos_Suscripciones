import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RenewalPublicPage } from './renewal-public.page';
import { PublicRenewal } from './renewals.api';

const open: PublicRenewal = {
  status: 'OPEN', response: 'NONE', store: { slug: 'mi-tienda', businessName: 'Mi Tienda', logoUrl: null, primaryColor: null },
  customerFirstName: 'María', productName: 'Servicio Premium', durationDays: 30, price: 18, currentExpiresAt: '2099-11-01T00:00:00Z',
  canRespond: true, canPay: true, paymentMethods: [{ method: 'YAPE', label: 'Yape' }], farewellMessage: null, optedOut: false,
};

describe('RenewalPublicPage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [RenewalPublicPage], providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });

  async function render() {
    const fixture = TestBed.createComponent(RenewalPublicPage);
    fixture.componentRef.setInput('token', 'tok');
    fixture.detectChanges();
    http.expectOne('/api/v1/public/renewals/tok').flush(open);
    await fixture.whenStable();
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  const button = (el: HTMLElement, text: string) => Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.includes(text))!;

  it('responder «Sí» registra la intención y NO afirma que ya se renovó', async () => {
    const { fixture, el } = await render();
    expect(el.textContent).toContain('Hola, María.');
    button(el, 'Sí, renovar').click();
    const req = http.expectOne('/api/v1/public/renewals/tok/response');
    expect(req.request.body).toEqual({ response: 'ACCEPTED' });
    req.flush({ ...open, status: 'AWAITING_PAYMENT', response: 'ACCEPTED', canRespond: false });
    fixture.detectChanges();
    expect(el.textContent).toContain('se confirmará cuando Mi Tienda verifique tu pago');
    expect(el.textContent).not.toContain('fue confirmada');
  });

  it('pagar crea el pedido de renovación y lleva a la página del pedido', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const { fixture, el } = await render();
    button(el, 'Sí, renovar').click();
    http.expectOne('/api/v1/public/renewals/tok/response').flush({ ...open, status: 'AWAITING_PAYMENT', response: 'ACCEPTED', canRespond: false });
    fixture.detectChanges();
    button(el, 'Continuar al pago').click();
    const req = http.expectOne('/api/v1/public/renewals/tok/orders');
    expect(req.request.body).toEqual({ paymentMethod: 'YAPE' });
    req.flush({ publicToken: 'order-tok', orderNumber: 'ORD-2026-000009' });
    expect(navigate).toHaveBeenCalledWith(['/pedido', 'order-tok']);
  });

  it('rechazar muestra la despedida', async () => {
    const { fixture, el } = await render();
    button(el, 'No, gracias').click();
    http.expectOne('/api/v1/public/renewals/tok/response').flush({
      ...open, status: 'DECLINED', response: 'DECLINED', canRespond: false, canPay: false,
      farewellMessage: 'Gracias por haber confiado en Mi Tienda, María.',
    });
    fixture.detectChanges();
    expect(el.textContent).toContain('Gracias por haber confiado en Mi Tienda, María.');
  });

  it('un enlace fuera del plazo no ofrece responder ni pagar', () => {
    const fixture = TestBed.createComponent(RenewalPublicPage);
    fixture.componentRef.setInput('token', 'tok');
    fixture.detectChanges();
    http.expectOne('/api/v1/public/renewals/tok').flush({ ...open, canRespond: false, canPay: false });
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('ya no admite cambios');
    expect(Array.from(el.querySelectorAll('button')).some((b) => b.textContent?.includes('Sí, renovar'))).toBe(false);
  });
});
