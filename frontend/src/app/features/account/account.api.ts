import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

const API = '/api/v1/auth';

export interface TwoFactorStatus {
  enabled: boolean;
  enabledAt: string | null;
  recoveryCodesLeft: number;
  required: boolean;
}

export interface TwoFactorSetup {
  /** Clave agrupada de 4 en 4, para escribirla a mano si no se puede escanear el QR. */
  secret: string;
  otpauthUri: string;
  qrDataUrl: string;
}

export interface OpenSession {
  id: string;
  device: string;
  ipAddress: string | null;
  createdAt: string;
  lastUsedAt: string;
  current: boolean;
}

@Injectable({ providedIn: 'root' })
export class AccountApi {
  private readonly http = inject(HttpClient);

  twoFactorStatus(): Observable<TwoFactorStatus> {
    return this.http.get<TwoFactorStatus>(`${API}/2fa`);
  }

  startTwoFactorSetup(password: string): Observable<TwoFactorSetup> {
    return this.http.post<TwoFactorSetup>(`${API}/2fa/setup`, { password });
  }

  enableTwoFactor(code: string): Observable<{ recoveryCodes: string[]; otherSessionsClosed: number }> {
    return this.http.post<{ recoveryCodes: string[]; otherSessionsClosed: number }>(`${API}/2fa/enable`, { code });
  }

  disableTwoFactor(password: string, code: string): Observable<void> {
    return this.http.post<void>(`${API}/2fa/disable`, { password, code });
  }

  regenerateRecoveryCodes(code: string): Observable<{ recoveryCodes: string[] }> {
    return this.http.post<{ recoveryCodes: string[] }>(`${API}/2fa/recovery-codes`, { code });
  }

  sessions(): Observable<OpenSession[]> {
    return this.http.get<OpenSession[]>(`${API}/sessions`);
  }

  revokeSession(id: string): Observable<void> {
    return this.http.delete<void>(`${API}/sessions/${id}`);
  }

  revokeOtherSessions(): Observable<{ closed: number }> {
    return this.http.post<{ closed: number }>(`${API}/sessions/revoke-others`, {});
  }
}

export function formatDateTime(iso: string): string {
  return new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(iso));
}
