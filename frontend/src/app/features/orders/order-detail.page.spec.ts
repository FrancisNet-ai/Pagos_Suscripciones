import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { OrderDetailPage } from './order-detail.page';

const reported = {
  id: 'o1', orderNumber: 'ORD-2026-000001', status: 'PAYMENT_REPORTED', statusLabel: 'Por verificar', customerId: 'c1',
  customerName: 'Carlos', customerPhone: '+51987654321', customerWhatsappUrl: 'https://wa.me/51987654321', amount: 15, currency: 'PEN',
  items: [{ productId: 'p1', productName: 'Servicio Premium', productType: 'SUBSCRIPTION', unitPrice: 15, durationDays: 30, quantity: 1 }],
  payments: [{ id: 'pay1', method: 'YAPE', holderName: 'Carla', receiverPhone: '+51911222333', amount: 15, status: 'PENDING_VERIFICATION',
    transactionReference: '12345678', hasReceipt: false, reportedAt: '2026-10-27T10:00:00Z', verifiedAt: null, rejectedAt: null, rejectionReason: null }],
  capacityStatus: null, reservedUntil: null, incidentReason: null, cancellationReason: null, renewalRemindersConsent: false,
  createdAt: '2026-10-27T09:00:00Z', paidAt: null,
};

describe('OrderDetailPage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [OrderDetailPage], providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });

  it('aprobar exige confirmar la recepción del dinero', async () => {
    const fixture = TestBed.createComponent(OrderDetailPage);
    fixture.componentRef.setInput('id', 'o1');
    fixture.detectChanges();
    http.expectOne('/api/v1/orders/o1').flush(reported);
    await fixture.whenStable();
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;

    const approve = Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.includes('Aprobar pago'))!;
    expect(approve.disabled).toBe(true);
    el.querySelector<HTMLInputElement>('input[type=checkbox]')!.click();
    fixture.detectChanges();
    expect(approve.disabled).toBe(false);
    approve.click();
    http.expectOne({ method: 'POST', url: '/api/v1/payments/pay1/verify' }).flush({ ...reported, status: 'PAID', statusLabel: 'Pago verificado',
      payments: [{ ...reported.payments[0], status: 'VERIFIED' }] });
    fixture.detectChanges();
    expect(el.textContent).toContain('Pago verificado');
    expect(Array.from(el.querySelectorAll('button')).some((b) => b.textContent?.includes('Aprobar pago'))).toBe(false);
  });
});
