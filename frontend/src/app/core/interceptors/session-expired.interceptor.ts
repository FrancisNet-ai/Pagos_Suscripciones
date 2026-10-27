import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../auth/auth.service';

/** Si una petición autenticada recibe 401, la sesión expiró: se limpia y se envía al login. */
export const sessionExpiredInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return next(req).pipe(
    catchError((error: unknown) => {
      const isAuthEndpoint = req.url.startsWith('/api/v1/auth/');
      if (error instanceof HttpErrorResponse && error.status === 401 && !isAuthEndpoint && auth.isAuthenticated()) {
        auth.clearLocalSession();
        void router.navigate(['/ingresar'], { queryParams: { volver: router.url, motivo: 'sesion' } });
      }
      return throwError(() => error);
    }),
  );
};
