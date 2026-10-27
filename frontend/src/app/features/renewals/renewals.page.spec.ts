import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { NotificationItem } from './renewals.api';
import { RenewalsPage } from './renewals.page';

const notification: NotificationItem = {
  id: 'n1', type: 'REMINDER_BEFORE', typeLabel: 'Recordatorio previo', customerId: 'c1', customerName: 'María', customerPhone: '+51911000001',
  productName: 'Servicio Premium', scheduledAt: '2026-10-27T10:00:00Z', message: 'Hola, María.', whatsappUrl: 'https://wa.me/51911000001?text=Hola', consent: true,
};
const overview = { notificationsDue: 1, upcoming: 1, accepted: 0, declined: 0, noResponse: 0, completed: 0, capacityReleasePending: 0 };
const page = (items: unknown[], totalPages = 1, current = 0) => ({ items, page: current, size: 20, totalItems: totalPages > 1 ? 21 : items.length, totalPages });
const button = (el: HTMLElement, text: string) => Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.includes(text))!;

describe('RenewalsPage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [RenewalsPage], providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify({ ignoreCancelled: true }));

  async function render(items = [notification], totalPages = 1) {
    const fixture = TestBed.createComponent(RenewalsPage);
    fixture.detectChanges();
    await fixture.whenStable();
    http.match('/api/v1/renewals/overview').forEach((r) => r.flush(overview));
    http.expectOne((r) => r.url === '/api/v1/renewals/notifications').flush(page(items, totalPages));
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('«Ya lo envié» solo se habilita después de abrir WhatsApp y se confirma una sola vez', async () => {
    const { fixture, el } = await render();
    const sent = button(el, 'Ya lo envié');
    expect(sent.disabled).toBe(true);
    const link = el.querySelector<HTMLAnchorElement>('a[href^="https://wa.me/"]')!;
    link.addEventListener('click', (e) => e.preventDefault());
    link.click();
    fixture.detectChanges();
    expect(sent.disabled).toBe(false);
    sent.click();
    fixture.detectChanges();
    expect(sent.disabled).toBe(true);
    http.expectOne({ method: 'POST', url: '/api/v1/renewals/notifications/n1/sent' }).flush({});
    http.match('/api/v1/renewals/overview').forEach((r) => r.flush({ ...overview, notificationsDue: 0 }));
    http.expectOne((r) => r.url === '/api/v1/renewals/notifications').flush(page([]));
    fixture.detectChanges();
    expect(el.textContent).toContain('No tienes avisos pendientes.');
  });

  it('no ofrece envío ni confirmación de avisos sin consentimiento', async () => {
    const { el } = await render([{ ...notification, consent: false, whatsappUrl: null }]);
    expect(el.textContent).toContain('no autorizó recordatorios');
    expect(el.querySelector('a[href^="https://wa.me/"]')).toBeNull();
    expect(button(el, 'Ya lo envié').disabled).toBe(true);
    expect(button(el, 'Omitir').disabled).toBe(false);
  });

  it('permite llegar a la segunda página de avisos', async () => {
    const { fixture, el } = await render([notification], 2);
    button(el, 'Siguiente').click();
    const request = http.expectOne((r) => r.url === '/api/v1/renewals/notifications' && r.params.get('page') === '1');
    request.flush(page([{ ...notification, id: 'n21', customerName: 'Última clienta' }], 2, 1));
    http.match('/api/v1/renewals/overview').forEach((r) => r.flush(overview));
    fixture.detectChanges();
    expect(el.textContent).toContain('Última clienta');
    expect(el.textContent).toContain('Página 2 de 2');
    expect(button(el, 'Siguiente').disabled).toBe(true);
  });

  it('cancela cargas anteriores al cambiar de grupo para no mezclar resultados', async () => {
    const { fixture, el } = await render();
    button(el, 'Aceptaron').click();
    const accepted = http.expectOne((r) => r.url === '/api/v1/renewals' && r.params.get('bucket') === 'ACCEPTED');
    button(el, 'Rechazaron').click();
    expect(accepted.cancelled).toBe(true);
    http.expectOne((r) => r.url === '/api/v1/renewals' && r.params.get('bucket') === 'DECLINED').flush(page([]));
    http.match('/api/v1/renewals/overview').forEach((r) => r.flush(overview));
    fixture.detectChanges();
    expect(el.textContent).toContain('No hay renovaciones en este grupo.');
  });

  it('muestra errores de carga y permite reintentar', async () => {
    const { fixture, el } = await render();
    button(el, 'Aceptaron').click();
    http.expectOne((r) => r.url === '/api/v1/renewals').flush({ detail: 'No pudimos cargar las renovaciones.' }, { status: 503, statusText: 'Unavailable' });
    http.match('/api/v1/renewals/overview').forEach((r) => r.flush(overview));
    fixture.detectChanges();
    expect(el.querySelector('[aria-busy=true]')).toBeNull();
    button(el, 'Volver a intentar').click();
    http.expectOne((r) => r.url === '/api/v1/renewals').flush(page([]));
    http.match('/api/v1/renewals/overview').forEach((r) => r.flush(overview));
    fixture.detectChanges();
    expect(el.textContent).toContain('No hay renovaciones en este grupo.');
  });
});
