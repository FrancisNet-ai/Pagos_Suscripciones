import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { SessionResponse, StaffPermission } from '../../core/auth/auth.models';
import { AuthService } from '../../core/auth/auth.service';
import { DashboardShellComponent } from './dashboard-shell.component';

function session(role: 'OWNER' | 'STAFF', permissions: StaffPermission[]): SessionResponse {
  return {
    user: { id: 'u', fullName: 'Luis Apoyo', email: 'luis@ejemplo.pe', phone: '+51987654321', roles: [role === 'OWNER' ? 'BUSINESS_OWNER' : 'BUSINESS_STAFF'] },
    business: {
      id: 'b', uniqueCode: 'NV-1', slug: 'norte', businessName: 'Norte Digital', status: 'ACTIVE', role, permissions,
      access: { active: true, block: null, message: null, subscriptionStatus: 'ACTIVE', planName: 'Crece', expiresAt: null, usableUntil: null, features: [] },
    },
  };
}

function render(s: SessionResponse) {
  TestBed.configureTestingModule({ imports: [DashboardShellComponent], providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
  TestBed.inject(AuthService).startSession(s);
  const fixture = TestBed.createComponent(DashboardShellComponent);
  fixture.detectChanges();
  const el = fixture.nativeElement as HTMLElement;
  return Array.from(el.querySelectorAll('#panel-nav a')).map((a) => a.textContent?.trim() ?? '');
}

describe('DashboardShellComponent', () => {
  it('el colaborador solo ve sus secciones, sin configuración ni plan', () => {
    const links = render(session('STAFF', ['ORDERS']));
    expect(links).toContain('Pedidos');
    // Aprobar pagos es otro permiso: sin él no aparece «Por verificar».
    expect(links).not.toContain('Por verificar');
    expect(links).toContain('Pendientes');
    expect(links).not.toContain('Productos');
    expect(links).not.toContain('Reportes');
    expect(links).not.toContain('Equipo');
    expect(links).not.toContain('Mi plan');
  });

  it('el titular ve todo, incluido el equipo', () => {
    const links = render(session('OWNER', ['PRODUCTS', 'ORDERS', 'CUSTOMERS', 'RENEWALS', 'REPORTS']));
    expect(links).toContain('Productos');
    expect(links).toContain('Equipo');
    expect(links).toContain('Mi plan');
  });

  it('con varios negocios muestra el selector para cambiar de negocio', () => {
    const s = session('OWNER', ['PRODUCTS', 'ORDERS', 'PAYMENTS', 'CUSTOMERS', 'RENEWALS', 'REPORTS']);
    s.memberships = [
      { businessId: 'b', businessName: 'Norte Digital', role: 'OWNER' },
      { businessId: 'c', businessName: 'Sur Digital', role: 'STAFF' },
    ];
    TestBed.configureTestingModule({ imports: [DashboardShellComponent], providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    TestBed.inject(AuthService).startSession(s);
    const fixture = TestBed.createComponent(DashboardShellComponent);
    fixture.detectChanges();
    const select = (fixture.nativeElement as HTMLElement).querySelector<HTMLSelectElement>('#business-switch')!;
    expect(Array.from(select.options).map((o) => o.textContent?.trim())).toEqual(['Norte Digital', 'Sur Digital · colaborador']);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Crear otro negocio');
  });
});
