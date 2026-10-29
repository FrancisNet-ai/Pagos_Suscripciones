import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { Role, StaffPermission } from '../auth/auth.models';
import { authGuard, guestGuard, ownerGuard, ownerMatch, permissionGuard, roleGuard, twoFactorSetupGuard } from './auth.guards';

function setup(roles: Role[] | null, permissions: StaffPermission[] = [], mustSetUpTwoFactor = false) {
  const authMock = {
    mustSetUpTwoFactor: () => mustSetUpTwoFactor,
    isOwner: () => roles?.includes('BUSINESS_OWNER') ?? false,
    can: (p: StaffPermission) => permissions.includes(p),
    isAuthenticated: () => roles !== null,
    hasRole: (role: Role) => roles?.includes(role) ?? false,
    homeRoute: () => (roles?.includes('SUPER_ADMIN') ? '/admin' : '/panel'),
  };
  TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: AuthService, useValue: authMock }] });
  return TestBed.inject(Router);
}

const route = {} as ActivatedRouteSnapshot;
const state = (url: string) => ({ url }) as RouterStateSnapshot;

describe('guards', () => {
  it('authGuard redirige al login conservando la ruta', () => {
    const router = setup(null);
    const result = TestBed.runInInjectionContext(() => authGuard(route, state('/panel'))) as UrlTree;
    expect(router.serializeUrl(result)).toBe('/ingresar?volver=%2Fpanel');
  });

  it('authGuard permite el paso con sesión', () => {
    setup(['BUSINESS_OWNER']);
    expect(TestBed.runInInjectionContext(() => authGuard(route, state('/panel')))).toBe(true);
  });

  it('guestGuard envía al panel si ya hay sesión', () => {
    const router = setup(['BUSINESS_OWNER']);
    const result = TestBed.runInInjectionContext(() => guestGuard(route, state('/ingresar'))) as UrlTree;
    expect(router.serializeUrl(result)).toBe('/panel');
  });

  it('roleGuard bloquea roles no autorizados', () => {
    const router = setup(['SUPER_ADMIN']);
    const result = TestBed.runInInjectionContext(() => roleGuard('BUSINESS_OWNER')(route, state('/panel'))) as UrlTree;
    expect(router.serializeUrl(result)).toBe('/admin');
  });

  it('el colaborador entra solo a las secciones que tiene habilitadas', () => {
    const router = setup(['BUSINESS_STAFF'], ['ORDERS']);
    expect(TestBed.runInInjectionContext(() => permissionGuard('ORDERS')(route, state('/panel/ventas')))).toBe(true);
    const denied = TestBed.runInInjectionContext(() => permissionGuard('PRODUCTS')(route, state('/panel/productos'))) as UrlTree;
    expect(router.serializeUrl(denied)).toBe('/panel');
  });

  it('configuración, plan y equipo son solo del titular', () => {
    const router = setup(['BUSINESS_STAFF'], ['ORDERS', 'PRODUCTS', 'CUSTOMERS', 'RENEWALS', 'REPORTS']);
    const denied = TestBed.runInInjectionContext(() => ownerGuard(route, state('/panel/configuracion'))) as UrlTree;
    expect(router.serializeUrl(denied)).toBe('/panel');
    expect(TestBed.runInInjectionContext(() => ownerMatch({}, []))).toBe(false);
  });

  it('el titular pasa los controles de propietario', () => {
    setup(['BUSINESS_OWNER']);
    expect(TestBed.runInInjectionContext(() => ownerGuard(route, state('/panel/plan')))).toBe(true);
    expect(TestBed.runInInjectionContext(() => ownerMatch({}, []))).toBe(true);
  });

  it('el superadministrador sin verificación en dos pasos solo entra a «Mi cuenta»', () => {
    const router = setup(['SUPER_ADMIN'], [], true);
    const result = TestBed.runInInjectionContext(() => twoFactorSetupGuard(route, state('/admin/planes'))) as UrlTree;
    expect(router.serializeUrl(result)).toBe('/admin/cuenta');
    expect(TestBed.runInInjectionContext(() => twoFactorSetupGuard(route, state('/admin/cuenta')))).toBe(true);
  });

  it('con la verificación activa el superadministrador entra a todo /admin', () => {
    setup(['SUPER_ADMIN']);
    expect(TestBed.runInInjectionContext(() => twoFactorSetupGuard(route, state('/admin/planes')))).toBe(true);
  });
});
