import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AdminPaymentsPage } from './admin-payments.page';

describe('AdminPaymentsPage', () => {
  it('aprobar exige confirmar que el dinero llegó', () => {
    TestBed.configureTestingModule({ imports: [AdminPaymentsPage], providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(AdminPaymentsPage);
    fixture.detectChanges();
    http.expectOne((r) => r.url === '/api/v1/admin/saas-payments' && r.params.get('status') === 'PENDING_VERIFICATION').flush({
      items: [{ id: 's1', businessId: 'b1', businessName: 'Digital Streaming', invoiceId: 'i1', invoiceNumber: 'PLAN-2026-000001', planName: 'Emprende',
        method: 'YAPE', receiverHolder: 'Plataforma SAC', receiverPhone: '+51955111222', amount: 49.9, transactionReference: 'OP-1', hasReceipt: false,
        status: 'PENDING_VERIFICATION', reportedAt: '2026-10-27T10:05:00Z', verifiedAt: null, rejectionReason: null }],
      page: 0, size: 20, totalItems: 1, totalPages: 1,
    });
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const approve = Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.includes('Aprobar'))!;
    expect(approve.disabled).toBe(true);
    el.querySelector<HTMLInputElement>('input[type="checkbox"]')!.click();
    fixture.detectChanges();
    expect(approve.disabled).toBe(false);
    approve.click();
    http.expectOne({ method: 'POST', url: '/api/v1/admin/saas-payments/s1/verify' }).flush(null);
    http.expectOne((r) => r.url === '/api/v1/admin/saas-payments').flush({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 });
    fixture.detectChanges();
    expect(el.textContent).toContain('Pago aprobado');
  });
});
