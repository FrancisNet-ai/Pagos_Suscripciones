import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, firstValueFrom, map, of, tap } from 'rxjs';
import {
  BusinessSummary,
  LoginPayload,
  RegisterPayload,
  Role,
  SessionResponse,
  StaffPermission,
  TwoFactorChallenge,
} from './auth.models';

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
  /** Titular del negocio (los colaboradores no ven configuración, plan ni equipo). */
  readonly isOwner = computed(() => this.business()?.role === 'OWNER');

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

  /** Abre la sesión o, si la cuenta tiene verificación en dos pasos, devuelve el desafío (HTTP 202). */
  login(payload: LoginPayload): Observable<SessionResponse | TwoFactorChallenge> {
    return this.http.post<SessionResponse | TwoFactorChallenge>(`${API}/login`, payload, { observe: 'response' }).pipe(
      map((response) => response.body!),
      tap((body) => {
        if (!isChallenge(body)) this.sessionState.set(body);
      }),
    );
  }

  /** Segundo paso del inicio de sesión: código de la app o código de recuperación. */
  completeSecondFactor(challengeToken: string, code: string): Observable<SessionResponse> {
    return this.http.post<SessionResponse>(`${API}/login/2fa`, { challengeToken, code }).pipe(tap((s) => this.sessionState.set(s)));
  }

  /** Refleja en la sesión que se activó o desactivó la verificación en dos pasos. */
  setTwoFactorEnabled(enabled: boolean): void {
    this.sessionState.update((s) => (s ? { ...s, user: { ...s.user, twoFactorEnabled: enabled } } : s));
  }

  /** Superadministrador que aún debe activar la verificación en dos pasos. */
  readonly mustSetUpTwoFactor = computed(() => {
    const user = this.user();
    return !!user?.twoFactorRequired && !user.twoFactorEnabled;
  });

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

  /** Refleja en la sesión los cambios del negocio (nombre, enlace) hechos desde Configuración. */
  updateBusinessSummary(changes: Partial<BusinessSummary>): void {
    this.sessionState.update((s) => (s?.business ? { ...s, business: { ...s.business, ...changes } } : s));
  }

  /** Limpia el estado local cuando el backend indica que la sesión ya no es válida. */
  clearLocalSession(): void {
    this.sessionState.set(null);
  }

  /** Solo decide qué se muestra: el backend valida cada petición del colaborador. */
  can(permission: StaffPermission): boolean {
    return this.business()?.permissions.includes(permission) ?? false;
  }

  /** Abre la sesión del colaborador que acaba de aceptar su invitación. */
  startSession(session: SessionResponse): void {
    this.sessionState.set(session);
  }

  /** Cambia el negocio activo de la sesión; el servidor valida la membresía. */
  switchBusiness(businessId: string): Observable<SessionResponse> {
    return this.http.post<SessionResponse>(`${API}/business`, { businessId }).pipe(tap((s) => this.sessionState.set(s)));
  }

  /** Crea otro negocio para la misma cuenta y lo deja activo. */
  createBusiness(businessName: string): Observable<SessionResponse> {
    return this.http.post<SessionResponse>(`${API}/businesses`, { businessName }).pipe(tap((s) => this.sessionState.set(s)));
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

export function isChallenge(body: SessionResponse | TwoFactorChallenge): body is TwoFactorChallenge {
  return (body as TwoFactorChallenge).twoFactorRequired === true;
}
