import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AdminBusinessDetailPage } from './admin-business-detail.page';

const detail = {
  summary: {
    id: 'b1', businessName: 'Tienda del titular', uniqueCode: 'NV-0001', slug: 'tienda-titular', status: 'ACTIVE', ownerName: 'Kevin',
    ownerEmail: 'kevin@example.test', planName: null, subscriptionStatus: null, expiresAt: null, createdAt: '2026-11-01T10:00:00Z',
  },
  ownerPhone: '+51987654321', suspensionReason: null, suspendedAt: null,
  access: { active: false, block: 'NO_PLAN' }, usage: { products: 0, customers: 0, ordersPaid: 0 },
  invoices: [], activity: [], supportAccessUntil: null,
};
const plan = (id: string, name: string, active: boolean) => ({
  id, name, description: null, price: 30, billingPeriod: 'MONTHLY', billingPeriodLabel: 'Mensual', productLimit: null, customerLimit: null,
  adminUserLimit: 1, featureReports: true, featureReminders: true, featureBranding: true, active, sortOrder: 0,
});

describe('AdminBusinessDetailPage', () => {
  it('da un plan de cortesía con plan oculto, duración y motivo', () => {
    TestBed.configureTestingModule({ imports: [AdminBusinessDetailPage], providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(AdminBusinessDetailPage);
    fixture.componentRef.setInput('id', 'b1');
    fixture.detectChanges();
    http.expectOne('/api/v1/admin/businesses/b1').flush(detail);
    http.expectOne('/api/v1/admin/plans').flush([plan('p1', 'Altoke', true), plan('p2', 'Interno', false)]);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;

    const planSelect = el.querySelector<HTMLSelectElement>('#gift-plan')!;
    expect(Array.from(planSelect.options).map((o) => o.textContent?.trim())).toEqual(['Altoke', 'Interno (oculto)']);
    planSelect.value = 'p2';
    planSelect.dispatchEvent(new Event('change'));
    const periods = el.querySelector<HTMLSelectElement>('#gift-periods')!;
    expect(periods.options[2].textContent?.trim()).toBe('3 meses');
    periods.selectedIndex = 2;
    periods.dispatchEvent(new Event('change'));

    const submit = Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.includes('Dar plan de cortesía'))!;
    submit.click();
    fixture.detectChanges();
    expect(el.textContent).toContain('Indica un motivo');
    http.expectNone('/api/v1/admin/businesses/b1/complimentary');

    const reason = el.querySelector<HTMLInputElement>('#gift-reason')!;
    reason.value = 'Tienda propia del titular';
    reason.dispatchEvent(new Event('input'));
    submit.click();
    const req = http.expectOne({ method: 'POST', url: '/api/v1/admin/businesses/b1/complimentary' });
    expect(req.request.body).toEqual({ planId: 'p2', periods: 3, reason: 'Tienda propia del titular' });
    req.flush({ ...detail, summary: { ...detail.summary, planName: 'Interno', subscriptionStatus: 'ACTIVE' } });
    fixture.detectChanges();
    expect(el.textContent).toContain('Plan Interno de cortesía activado.');
  });
});
