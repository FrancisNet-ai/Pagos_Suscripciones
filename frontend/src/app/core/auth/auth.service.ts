import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, firstValueFrom, map, of, tap } from 'rxjs';
import { LoginPayload, RegisterPayload, Role, SessionResponse } from './auth.models';

const API = '/api/v1/auth';

/**
 * Estado de sesión del vendedor. La credencial vive en una cookie HttpOnly gestionada por el
 * backend; aquí solo se guarda la información pública devuelta por `/auth/me`.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly sessionState = signal<SessionResponse | null>(null);

  readonly session = this.sessionState.asReadonly();
  readonly isAuthenticated = computed(() => this.sessionState() !== null);
  readonly user = computed(() => this.sessionState()?.user ?? null);
  readonly business = computed(() => this.sessionState()?.business ?? null);

  /** Se ejecuta al iniciar la aplicación: obtiene el token CSRF y recupera la sesión si existe. */
  async initialize(): Promise<void> {
    await firstValueFrom(this.http.get<void>(`${API}/csrf`).pipe(catchError(() => of(undefined))));
    await firstValueFrom(this.loadSession());
  }

  loadSession(): Observable<SessionResponse | null> {
    return this.http.get<SessionResponse>(`${API}/me`).pipe(
      catchError(() => of(null)),
      tap((session) => this.sessionState.set(session)),
    );
  }

  register(payload: RegisterPayload): Observable<SessionResponse> {
    return this.http.post<SessionResponse>(`${API}/register`, payload).pipe(tap((s) => this.sessionState.set(s)));
  }

  login(payload: LoginPayload): Observable<SessionResponse> {
    return this.http.post<SessionResponse>(`${API}/login`, payload).pipe(tap((s) => this.sessionState.set(s)));
  }

  logout(): Observable<void> {
    return this.http.post<void>(`${API}/logout`, {}).pipe(
      catchError(() => of(undefined)),
      map(() => undefined),
      tap(() => this.sessionState.set(null)),
    );
  }

  requestPasswordReset(email: string): Observable<void> {
    return this.http.post<void>(`${API}/password-reset`, { email });
  }

  confirmPasswordReset(token: string, newPassword: string): Observable<void> {
    return this.http.post<void>(`${API}/password-reset/confirm`, { token, newPassword });
  }

  /** Limpia el estado local cuando el backend indica que la sesión ya no es válida. */
  clearLocalSession(): void {
    this.sessionState.set(null);
  }

  hasRole(role: Role): boolean {
    return this.user()?.roles.includes(role) ?? false;
  }

  /** Ruta de inicio según el rol del usuario autenticado. */
  homeRoute(): string {
    if (this.hasRole('BUSINESS_OWNER') || this.hasRole('BUSINESS_STAFF')) return '/panel';
    if (this.hasRole('SUPER_ADMIN')) return '/admin';
    return '/';
  }
}
