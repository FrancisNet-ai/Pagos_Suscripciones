import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { TimeoutError, catchError, retry, throwError, timeout, timer } from 'rxjs';

/** Un único reintento de lecturas ante cortes transitorios; nunca repite pedidos, pagos ni cambios. */
export const resilientReadsInterceptor: HttpInterceptorFn = (request, next) => {
  // Las descargas de comprobantes pueden tardar más con mala conexión: no interrumpir binarios.
  if (
    request.method !== 'GET' ||
    request.responseType !== 'json' ||
    !request.url.startsWith('/api/v1/')
  )
    return next(request);

  return next(request).pipe(
    timeout(30_000),
    catchError((error: unknown) =>
      throwError(() =>
        error instanceof TimeoutError
          ? new HttpErrorResponse({
              status: 0,
              statusText: 'Tiempo de espera agotado',
              url: request.url,
              error,
            })
          : error,
      ),
    ),
    retry({
      count: 1,
      delay: (error: unknown) => {
        const transient =
          error instanceof HttpErrorResponse && [0, 502, 503, 504].includes(error.status);
        return transient && navigator.onLine ? timer(600) : throwError(() => error);
      },
    }),
  );
};
