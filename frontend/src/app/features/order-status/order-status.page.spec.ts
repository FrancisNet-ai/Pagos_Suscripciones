import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { OrderStatusPage } from './order-status.page';

const awaiting = {
  orderNumber: 'ORD-2026-000001', status: 'AWAITING_PAYMENT', statusLabel: 'Pendiente de pago', createdAt: '2026-10-27T10:00:00Z',
  store: { slug: 'mi-tienda', businessName: 'Mi Tienda', logoUrl: null, primaryColor: null },
  customerName: 'Carlos', customerPhoneMasked: '*** *** 321', productName: 'Servicio Premium', productType: 'SUBSCRIPTION', durationDays: 30,
  amount: 15, currency: 'PEN', reservedUntil: null, canReportPayment: true, whatsappUrl: 'https://wa.me/51912345678?text=Hola',
  payment: {
    status: 'PENDING', transactionReference: null, reportedAt: null, rejectionReason: null,
    instructions: { method: 'YAPE', methodLabel: 'Yape', holderName: 'Carla Ramírez', phone: '+51911222333', qrImageUrl: null, amount: 15, reference: 'ORD-2026-000001', automaticVerification: false },
  },
};

describe('OrderStatusPage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [OrderStatusPage], providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });

  async function render() {
    const fixture = TestBed.createComponent(OrderStatusPage);
    fixture.componentRef.setInput('token', 'tok');
    fixture.detectChanges();
    http.expectOne('/api/v1/public/orders/tok').flush(awaiting);
    await fixture.whenStable();
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('muestra titular declarado, número, importe y advertencia de comprobar destinatario', async () => {
    const { el } = await render();
    const text = el.textContent!;
    expect(text).toContain('Titular declarado por el vendedor');
    expect(text).toContain('Carla Ramírez');
    expect(text).toContain('911 222 333');
    expect(text).toContain('comprueba que el destinatario sea «Carla Ramírez»');
    expect(text).toContain('no fue validado por el banco');
    expect(el.querySelector('a[href^="https://wa.me/"]')).not.toBeNull();
  });

  it('reportar muestra el estado devuelto por el servidor, nunca «verificado» por sí solo', async () => {
    const { fixture, el } = await render();
    const input = el.querySelector<HTMLInputElement>('#transactionReference')!;
    input.value = '12345678';
    input.dispatchEvent(new Event('input'));
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();

    const req = http.expectOne('/api/v1/public/orders/tok/payment-report');
    expect((req.request.body as FormData).get('transactionReference')).toBe('12345678');
    req.flush({ ...awaiting, status: 'PAYMENT_REPORTED', statusLabel: 'Pendiente de verificación', canReportPayment: false,
      payment: { ...awaiting.payment, status: 'PENDING_VERIFICATION', transactionReference: '12345678' } });
    fixture.detectChanges();
    expect(el.querySelector('h1')!.textContent).toContain('Pendiente de verificación');
    expect(el.textContent).not.toContain('Pago verificado');
    expect(el.querySelector('form')).toBeNull();
  });
});
