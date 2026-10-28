import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { SessionResponse } from '../../core/auth/auth.models';
import { AuthService } from '../../core/auth/auth.service';
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

  function asRole(role: 'OWNER' | 'STAFF') {
    const session: SessionResponse = {
      user: { id: 'u', fullName: 'Ana', email: 'a@ejemplo.pe', phone: '+51987654321', roles: [role === 'OWNER' ? 'BUSINESS_OWNER' : 'BUSINESS_STAFF'] },
      business: {
        id: 'b', uniqueCode: 'NV-1', slug: 'norte', businessName: 'Norte', status: 'ACTIVE', role, permissions: ['ORDERS'],
        access: { active: true, block: null, message: null, subscriptionStatus: 'ACTIVE', planName: 'Crece', expiresAt: null, usableUntil: null, features: [] },
      },
    };
    TestBed.inject(AuthService).startSession(session);
  }

  const paid = { ...reported, status: 'PAID', statusLabel: 'Pago verificado',
    payments: [{ ...reported.payments[0], status: 'VERIFIED', verifiedAt: '2026-10-27T11:00:00Z' }] };

  async function render() {
    const fixture = TestBed.createComponent(OrderDetailPage);
    fixture.componentRef.setInput('id', 'o1');
    fixture.detectChanges();
    http.expectOne('/api/v1/orders/o1').flush(paid);
    await fixture.whenStable();
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('el titular registra un reembolso solo tras confirmar que devolvió el dinero', async () => {
    asRole('OWNER');
    const { fixture, el } = await render();
    Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.includes('Registrar un reembolso'))!.click();
    fixture.detectChanges();
    const reason = el.querySelector<HTMLTextAreaElement>('textarea')!;
    reason.value = 'El servicio no funcionó';
    reason.dispatchEvent(new Event('input'));
    const submit = Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.trim() === 'Registrar reembolso')!;
    fixture.detectChanges();
    expect(submit.disabled).toBe(true);
    Array.from(el.querySelectorAll<HTMLInputElement>('input[type=checkbox]')).at(-1)!.click();
    fixture.detectChanges();
    expect(submit.disabled).toBe(false);
    submit.click();
    const req = http.expectOne({ method: 'POST', url: '/api/v1/payments/pay1/refund' });
    expect(req.request.body).toEqual({ reason: 'El servicio no funcionó', refundReference: null });
  });

  it('un colaborador no ve la opción de reembolso', async () => {
    asRole('STAFF');
    const { el } = await render();
    expect(el.textContent).not.toContain('Registrar un reembolso');
  });
});
