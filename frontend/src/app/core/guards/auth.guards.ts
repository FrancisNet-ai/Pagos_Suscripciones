import { inject } from '@angular/core';
import { CanActivateChildFn, CanActivateFn, CanMatchFn, Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { Role, StaffPermission } from '../auth/auth.models';

/** Requiere sesión. La autorización real la aplica siempre el backend. */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  if (auth.isAuthenticated()) return true;
  return inject(Router).createUrlTree(['/ingresar'], { queryParams: { volver: state.url } });
};

/** Pantallas solo para visitantes (login, registro): si ya hay sesión, se va al panel. */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.isAuthenticated() ? inject(Router).parseUrl(auth.homeRoute()) : true;
};

export function roleGuard(...roles: Role[]): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    if (roles.some((role) => auth.hasRole(role))) return true;
    return inject(Router).parseUrl(auth.isAuthenticated() ? auth.homeRoute() : '/ingresar');
  };
}

/** Pantallas exclusivas del titular (configuración, plan, equipo). */
export const ownerGuard: CanActivateFn = () =>
  inject(AuthService).isOwner() ? true : inject(Router).parseUrl('/panel');

/** Sección del panel que requiere alguno de los permisos indicados. */
export function permissionGuard(...permissions: StaffPermission[]): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    return permissions.some((p) => auth.can(p)) ? true : inject(Router).parseUrl('/panel');
  };
}

/** Elige entre dos rutas con la misma URL según sea titular o colaborador. */
export const ownerMatch: CanMatchFn = () => inject(AuthService).isOwner();

/** Sin verificación en dos pasos, el superadministrador solo puede entrar a «Mi cuenta» (el backend también lo exige). */
export const twoFactorSetupGuard: CanActivateChildFn = (_route, state) => {
  const auth = inject(AuthService);
  if (!auth.mustSetUpTwoFactor() || state.url.startsWith('/admin/cuenta')) return true;
  return inject(Router).parseUrl('/admin/cuenta');
};
