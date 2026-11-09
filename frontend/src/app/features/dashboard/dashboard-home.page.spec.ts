import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { Dashboard } from '../reports/reports.api';
import { DashboardHomePage } from './dashboard-home.page';

const dashboard: Dashboard = {
  timeZone: 'America/Lima',
  confirmedToday: { amount: 20, count: 2 },
  confirmedMonth: { amount: 160, count: 16 },
  pendingVerification: { amount: 999, count: 2 },
  renewalsMonth: { amount: 40, count: 2 },
  notificationsDue: 0,
  customers: 8,
  subscriptions: {
    active: 5,
    pendingActivation: 1,
    expiringSoon: 2,
    expired: 3,
    capacityReleasePending: 1,
  },
  soldOutProducts: [],
  last30Days: Array.from({ length: 30 }, (_, i) => ({
    date: new Date(Date.UTC(2026, 9, 11 + i)).toISOString().slice(0, 10),
    amount: i < 23 ? 10 : 20,
    count: i < 23 ? 1 : 2,
  })),
  recentActivity: [
    {
      at: '2026-11-09T15:00:00Z',
      text: 'Pago verificado · ORD-2026-000001',
      entityType: 'PAYMENT',
      entityId: 'payment-uuid',
    },
  ],
};

describe('Inicio del vendedor: ingresos claros', () => {
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [DashboardHomePage],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(AuthService).startSession({
      user: {
        id: 'u',
        fullName: 'Carlos Prueba',
        email: 'prueba@example.test',
        phone: '+51987654321',
        roles: ['BUSINESS_OWNER'],
      },
      business: null,
    });
  });
  afterEach(() => {
    http.verify();
    vi.useRealTimers();
  });

  function render(data: Dashboard = dashboard) {
    const fixture = TestBed.createComponent(DashboardHomePage);
    fixture.detectChanges();
    http
      .expectOne('/api/v1/business/me')
      .flush({
        id: 'b',
        businessName: 'Negocio de prueba',
        slug: 'negocio-prueba',
        logoUrl: '/logo.webp',
      });
    http.expectOne('/api/v1/business/me/payment-methods').flush([{ enabled: true }]);
    http.expectOne('/api/v1/products?filter=PUBLISHED&size=1').flush({ totalItems: 1 });
    http.expectOne('/api/v1/reports/dashboard').flush(data);
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('separa lo cobrado este mes, hoy y pendiente, sin sumar renovaciones dos veces', () => {
    const { el } = render();
    expect(el.querySelector('.metric-income')?.textContent).toContain('160.00');
    expect(el.querySelector('.balance-today')?.textContent).toContain('20.00');
    expect(el.querySelector('.balance-pending')?.textContent).toContain('999.00');
    expect(el.querySelector('.income-chart-footer')?.textContent).toContain('Ya incluidas');
    expect(el.querySelector('.balance-pending a')?.getAttribute('href')).toBe(
      '/panel/ventas?filtro=TO_VERIFY',
    );
    expect(el.querySelector('.income-activity a')?.getAttribute('href')).toBe('/panel/ventas');
  });

  it('cambia entre 7 y 30 días con la serie real, sin consultas adicionales', () => {
    const { fixture, el } = render();
    expect(el.querySelector('.revenue-total')?.textContent).toContain('370.00');
    const seven = Array.from(el.querySelectorAll<HTMLButtonElement>('.income-period button')).find(
      (b) => b.textContent?.trim() === '7 días',
    )!;
    seven.click();
    fixture.detectChanges();
    expect(seven.getAttribute('aria-pressed')).toBe('true');
    expect(el.querySelector('.revenue-total')?.textContent).toContain('140.00');
    expect(el.querySelectorAll('app-revenue-chart tbody tr')).toHaveLength(7);
    expect(el.querySelector('.week-total')?.textContent).toContain('14');
    http.expectNone('/api/v1/reports/dashboard');
  });

  it('usa el mes del negocio cuando Lima todavía está en el día anterior a UTC', () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-11-01T02:00:00Z'));
    const { el } = render();
    expect(el.querySelector('.metric-income dt')?.textContent).toContain('octubre');
  });

  it('muestra cero sin inventar ventas cuando el negocio aún no tiene movimientos', () => {
    const { el } = render({
      ...dashboard,
      confirmedToday: { amount: 0, count: 0 },
      confirmedMonth: { amount: 0, count: 0 },
      pendingVerification: { amount: 0, count: 0 },
      renewalsMonth: { amount: 0, count: 0 },
      recentActivity: [],
      last30Days: dashboard.last30Days.map((day) => ({ ...day, amount: 0, count: 0 })),
    });
    expect(el.textContent).toContain('Aún no hay cobros verificados');
    expect(el.querySelector('.week-total')?.textContent).toContain('0');
    expect(el.querySelector('.balance-pending')?.textContent).toContain('Estás al día');
    expect(el.querySelector('.balance-pending a')).toBeNull();
  });
});
