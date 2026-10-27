import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { Role } from '../auth/auth.models';

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
