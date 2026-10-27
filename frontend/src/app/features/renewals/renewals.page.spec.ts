import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RenewalsPage } from './renewals.page';

describe('RenewalsPage', () => {
  it('«Ya lo envié» solo se habilita después de abrir WhatsApp', async () => {
    TestBed.configureTestingModule({ imports: [RenewalsPage], providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(RenewalsPage);
    fixture.detectChanges();
    await fixture.whenStable();
    http.match('/api/v1/renewals/overview').forEach((r) => r.flush({ notificationsDue: 1, upcoming: 1, accepted: 0, declined: 0, noResponse: 0, completed: 0, capacityReleasePending: 0 }));
    http.expectOne('/api/v1/renewals/notifications').flush({
      items: [{ id: 'n1', type: 'REMINDER_BEFORE', typeLabel: 'Recordatorio previo', customerId: 'c1', customerName: 'María', customerPhone: '+51911000001',
        productName: 'Servicio Premium', scheduledAt: '2026-10-27T10:00:00Z', message: 'Hola, María.', whatsappUrl: 'https://wa.me/51911000001?text=Hola', consent: false }],
      page: 0, size: 50, totalItems: 1, totalPages: 1,
    });
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const sent = Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.includes('Ya lo envié'))!;
    expect(sent.disabled).toBe(true);
    expect(el.textContent).toContain('no autorizó recordatorios');

    const link = el.querySelector<HTMLAnchorElement>('a[href^="https://wa.me/"]')!;
    link.addEventListener('click', (e) => e.preventDefault());
    link.click();
    fixture.detectChanges();
    expect(sent.disabled).toBe(false);
    sent.click();
    http.expectOne({ method: 'POST', url: '/api/v1/renewals/notifications/n1/sent' }).flush({});
    http.match('/api/v1/renewals/overview').forEach((r) => r.flush({ notificationsDue: 0, upcoming: 1, accepted: 0, declined: 0, noResponse: 0, completed: 0, capacityReleasePending: 0 }));
    fixture.detectChanges();
    expect(el.textContent).toContain('No tienes avisos pendientes.');
  });
});
