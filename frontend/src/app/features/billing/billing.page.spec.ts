import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { BillingOverview, Plan, planLines } from './billing.api';
import { BillingPage } from './billing.page';

const plan: Plan = {
  id: 'p1', name: 'Emprende', description: null, price: 49.9, billingPeriod: 'MONTHLY', billingPeriodLabel: 'Mensual',
  productLimit: 10, customerLimit: null, adminUserLimit: 3, featureReports: true, featureReminders: false, featureBranding: true, active: true, sortOrder: 1,
};

function overview(extra: Partial<BillingOverview> = {}): BillingOverview {
  return {
    access: { active: false, block: 'NO_PLAN', message: 'Elige un plan para empezar a vender.', subscriptionStatus: null, planName: null, expiresAt: null, usableUntil: null, features: [] },
    suspensionReason: null, currentPlan: null, startAt: null, graceDays: 5,
    products: { used: 0, limit: null }, customers: { used: 0, limit: null }, openInvoice: null,
    paymentOptions: [{ method: 'YAPE', methodLabel: 'Yape', holderName: 'Plataforma SAC', phone: '+51955111222', qrImageUrl: null }],
    support: { whatsapp: null, whatsappUrl: null, email: null }, plans: [plan], history: [],
    ...extra,
  };
}

describe('BillingPage', () => {
  function setup() {
    TestBed.configureTestingModule({ imports: [BillingPage], providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(BillingPage);
    fixture.detectChanges();
    return { http, fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('elegir un plan genera el cobro y muestra la cuenta de la plataforma con titular declarado', () => {
    const { http, fixture, el } = setup();
    http.expectOne('/api/v1/billing').flush(overview());
    fixture.detectChanges();
    expect(el.textContent).toContain('Sin plan vigente');

    Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.includes('Elegir este plan'))!.click();
    const req = http.expectOne({ method: 'POST', url: '/api/v1/billing/checkout' });
    expect(req.request.body).toEqual({ planId: 'p1' });
    req.flush(overview({
      access: { active: false, block: 'PENDING_PAYMENT', message: 'Tu plan se activará cuando verifiquemos tu pago.', subscriptionStatus: 'PENDING_PAYMENT', planName: 'Emprende', expiresAt: null, usableUntil: null, features: [] },
      openInvoice: { id: 'i1', invoiceNumber: 'PLAN-2026-000001', planId: 'p1', planName: 'Emprende', billingPeriod: 'MONTHLY', billingPeriodLabel: 'Mensual',
        kind: 'NEW', kindLabel: 'Alta', amount: 49.9, status: 'OPEN', periodStart: null, periodEnd: null, paidAt: null, createdAt: '2026-10-27T10:00:00Z', payments: [] },
    }));
    http.expectOne('/api/v1/auth/me').flush(null);
    fixture.detectChanges();
    expect(el.textContent).toContain('Cobro PLAN-2026-000001');
    expect(el.textContent).toContain('Titular declarado: Plataforma SAC');
    expect(el.textContent).toContain('955 111 222');
  });

  it('un pago en revisión no permite reportar otro ni cambiar de plan', () => {
    const { http, fixture, el } = setup();
    http.expectOne('/api/v1/billing').flush(overview({
      openInvoice: { id: 'i1', invoiceNumber: 'PLAN-2026-000001', planId: 'p1', planName: 'Emprende', billingPeriod: 'MONTHLY', billingPeriodLabel: 'Mensual',
        kind: 'NEW', kindLabel: 'Alta', amount: 49.9, status: 'OPEN', periodStart: null, periodEnd: null, paidAt: null, createdAt: '2026-10-27T10:00:00Z',
        payments: [{ id: 's1', method: 'YAPE', amount: 49.9, transactionReference: 'OP-1', hasReceipt: false, status: 'PENDING_VERIFICATION', reportedAt: '2026-10-27T10:05:00Z', verifiedAt: null, rejectionReason: null }] },
    }));
    fixture.detectChanges();
    expect(el.textContent).toContain('Pago en revisión');
    expect(el.querySelector('#ref')).toBeNull();
    const choose = Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.includes('Cobro generado'))!;
    expect(choose.disabled).toBe(true);
  });

  it('las líneas del plan indican cuántos usuarios admite (titular incluido)', () => {
    expect(planLines(plan)).toEqual([
      'Hasta 10 productos activos',
      'Clientes ilimitados',
      'Hasta 3 usuarios (titular incluido)',
      'Reportes de ventas',
      'Colores y portada propios en tu tienda',
    ]);
    expect(planLines({ ...plan, adminUserLimit: 1 })).toContain('Solo el titular');
  });
});
