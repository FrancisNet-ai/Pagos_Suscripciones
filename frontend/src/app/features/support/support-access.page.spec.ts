import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SupportAccessPage } from './support-access.page';

describe('SupportAccessPage', () => {
  it('autoriza por un tiempo limitado y muestra cada consulta de soporte', () => {
    TestBed.configureTestingModule({ imports: [SupportAccessPage], providers: [provideHttpClient(), provideHttpClientTesting()] });
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(SupportAccessPage);
    fixture.detectChanges();
    http.expectOne('/api/v1/business/me/support-access').flush({ active: null, grants: [], views: [] });
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Soporte no ha consultado tus datos.');

    Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.includes('Autorizar a soporte'))!.click();
    const req = http.expectOne({ method: 'POST', url: '/api/v1/business/me/support-access' });
    expect(req.request.body).toEqual({ hours: 4, note: null });
    req.flush({
      active: { id: 'g', grantedBy: 'Ana', note: null, createdAt: '2026-10-28T10:00:00Z', expiresAt: '2026-10-28T14:00:00Z', revokedAt: null, active: true },
      grants: [],
      views: [{ resource: 'Pedidos', reason: 'Pedido duplicado reportado', at: '2026-10-28T10:30:00Z' }],
    });
    fixture.detectChanges();
    expect(el.textContent).toContain('Retirar acceso ahora');
    expect(el.textContent).toContain('Pedido duplicado reportado');
  });
});
