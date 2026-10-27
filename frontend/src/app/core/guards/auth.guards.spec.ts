import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { Role } from '../auth/auth.models';
import { authGuard, guestGuard, roleGuard } from './auth.guards';

function setup(roles: Role[] | null) {
  const authMock = {
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
});
