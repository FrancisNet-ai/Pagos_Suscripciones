import { HttpErrorResponse } from '@angular/common/http';

export interface FieldError {
  field: string;
  message: string;
}

/** Error normalizado a partir de las respuestas `application/problem+json` del backend. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly fieldErrors: FieldError[] = [],
  ) {
    super(message);
  }

  static from(error: unknown): ApiError {
    if (error instanceof ApiError) return error;
    if (error instanceof HttpErrorResponse) {
      if (error.status === 0) {
        return new ApiError(0, 'NETWORK_ERROR', 'No pudimos conectar con el servidor. Revisa tu conexión e inténtalo de nuevo.');
      }
      const body = error.error as { code?: string; detail?: string; errors?: FieldError[] } | null;
      return new ApiError(
        error.status,
        body?.code ?? 'UNKNOWN',
        body?.detail ?? 'Ocurrió un error inesperado. Inténtalo nuevamente.',
        body?.errors ?? [],
      );
    }
    return new ApiError(-1, 'UNKNOWN', 'Ocurrió un error inesperado. Inténtalo nuevamente.');
  }

  fieldMessage(field: string): string | null {
    return this.fieldErrors.find((e) => e.field === field)?.message ?? null;
  }
}
