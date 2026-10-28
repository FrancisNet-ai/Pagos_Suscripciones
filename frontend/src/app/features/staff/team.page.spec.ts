import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Team } from './staff.api';
import { TeamPage } from './team.page';

const team: Team = {
  members: [
    {
      id: 'm1', userId: 'u1', fullName: 'Carla Titular', email: 'carla@ejemplo.pe', role: 'OWNER', status: 'ACTIVE',
      permissions: ['PRODUCTS', 'ORDERS', 'CUSTOMERS', 'RENEWALS', 'REPORTS'], since: '2026-10-01T10:00:00Z', revokedAt: null,
    },
    {
      id: 'm2', userId: 'u2', fullName: 'Luis Apoyo', email: 'luis@ejemplo.pe', role: 'STAFF', status: 'ACTIVE',
      permissions: ['ORDERS'], since: '2026-10-02T10:00:00Z', revokedAt: null,
    },
  ],
  invitations: [],
  seatsUsed: 2,
  seatLimit: 3,
  permissionOptions: [],
};

function render(data: Team) {
  TestBed.configureTestingModule({ imports: [TeamPage], providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
  const http = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(TeamPage);
  fixture.detectChanges();
  http.expectOne('/api/v1/business/me/staff').flush(data);
  fixture.detectChanges();
  return { http, fixture, el: fixture.nativeElement as HTMLElement };
}

function type(el: HTMLElement, selector: string, value: string) {
  const input = el.querySelector<HTMLInputElement>(selector)!;
  input.value = value;
  input.dispatchEvent(new Event('input'));
}

describe('TeamPage', () => {
  it('invita con las secciones elegidas y muestra el enlace una sola vez', () => {
    const { http, fixture, el } = render(team);
    expect(el.querySelector('[data-testid="seats"]')!.textContent).toContain('2 de 3 usuarios');
    expect(el.textContent).toContain('Pedidos y atención de compradores');

    type(el, '#staffName', 'Ana Ventas');
    type(el, '#staffEmail', 'ana@ejemplo.pe');
    const products = Array.from(el.querySelectorAll<HTMLLabelElement>('form label')).find((l) => l.textContent?.includes('Catálogo de productos'))!;
    products.querySelector('input')!.click();
    Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.includes('Crear invitación'))!.click();

    const req = http.expectOne({ method: 'POST', url: '/api/v1/business/me/staff/invitations' });
    expect(req.request.body).toEqual({ fullName: 'Ana Ventas', email: 'ana@ejemplo.pe', permissions: ['PRODUCTS'] });
    const invitation = {
      id: 'i1', fullName: 'Ana Ventas', email: 'ana@ejemplo.pe', permissions: ['PRODUCTS'], expiresAt: '2026-11-03T10:00:00Z',
      expired: false, createdAt: '2026-10-27T10:00:00Z',
    };
    req.flush({ invitation, link: 'https://nuvyra.test/invitacion/abc', emailSent: false, team: { ...team, invitations: [invitation], seatsUsed: 3 } });
    fixture.detectChanges();

    expect(el.querySelector('[data-testid="invitation-link"]')!.textContent).toContain('https://nuvyra.test/invitacion/abc');
    expect(el.textContent).toContain('no lo volveremos a mostrar');
    const share = Array.from(el.querySelectorAll('a')).find((a) => a.textContent?.includes('Compartir por WhatsApp'))!;
    expect(share.getAttribute('href')).toContain(encodeURIComponent('https://nuvyra.test/invitacion/abc'));
    // Con los 3 lugares en uso ya no se puede invitar.
    expect(Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.includes('Crear invitación'))!.disabled).toBe(true);
  });

  it('no envía la invitación sin ninguna sección', () => {
    const { http, el } = render(team);
    type(el, '#staffName', 'Ana Ventas');
    type(el, '#staffEmail', 'ana@ejemplo.pe');
    Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.includes('Crear invitación'))!.click();
    http.expectNone({ method: 'POST' });
  });

  it('el titular no tiene acciones sobre su propio acceso', () => {
    const { el } = render(team);
    const owner = el.querySelector('[data-testid="member-carla@ejemplo.pe"]')!;
    expect(owner.textContent).toContain('Titular');
    expect(owner.querySelector('button')).toBeNull();
    expect(el.querySelector('[data-testid="member-luis@ejemplo.pe"]')!.textContent).toContain('Revocar acceso');
  });
});
