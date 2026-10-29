import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { ApiError } from '../../core/services/api-error';
import { AccountApi, OpenSession, formatDateTime } from './account.api';

/** Sesiones abiertas en este y otros dispositivos, con la opción de cerrar las ajenas. */
@Component({
  selector: 'app-sessions-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="grid gap-4" aria-labelledby="sesiones-titulo">
      <h2 id="sesiones-titulo" class="type-h2">Sesiones abiertas</h2>
      <p class="-mt-2 text-sm">Si no reconoces un dispositivo, ciérralo y cambia tu contraseña.</p>
      @if (notice(); as n) { <p class="notice notice-success" role="status">{{ n }}</p> }
      @if (error(); as e) { <p class="notice notice-error" role="alert">{{ e }}</p> }
      <ul class="grid gap-3">
        @for (s of sessions(); track s.id) {
          <li class="dashboard-surface flex flex-wrap items-center justify-between gap-3">
            <div>
              <p class="font-semibold text-night">
                {{ s.device }}
                @if (s.current) { <span class="ml-2 text-sm font-normal text-[var(--text-muted)]">(este dispositivo)</span> }
              </p>
              <p class="text-sm text-[var(--text-muted)]">
                Último uso: {{ date(s.lastUsedAt) }} · Inició: {{ date(s.createdAt) }}@if (s.ipAddress) { · IP {{ s.ipAddress }} }
              </p>
            </div>
            @if (!s.current) {
              <button type="button" class="btn btn-secondary" [disabled]="busy()" (click)="revoke(s)">Cerrar</button>
            }
          </li>
        }
      </ul>
      @if (others() > 0) {
        <div><button type="button" class="btn btn-secondary" [disabled]="busy()" (click)="revokeOthers()">Cerrar todas las demás</button></div>
      }
    </section>
  `,
})
export class SessionsListComponent {
  private readonly api = inject(AccountApi);
  protected readonly sessions = signal<OpenSession[]>([]);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);

  constructor() {
    this.reload();
  }

  protected others(): number {
    return this.sessions().filter((s) => !s.current).length;
  }

  protected date(iso: string): string {
    return formatDateTime(iso);
  }

  protected revoke(session: OpenSession): void {
    this.act(this.api.revokeSession(session.id), () => `Cerramos la sesión de ${session.device}.`);
  }

  protected revokeOthers(): void {
    this.act(this.api.revokeOtherSessions(), (r) => `Cerramos ${r.closed} ${r.closed === 1 ? 'sesión' : 'sesiones'}.`);
  }

  /** Recarga la lista (p. ej. después de cambiar la contraseña, que cierra las demás sesiones). */
  reload(): void {
    this.api.sessions().subscribe({ next: (s) => this.sessions.set(s), error: (e: unknown) => this.error.set(ApiError.from(e).message) });
  }

  private act<T>(request: import('rxjs').Observable<T>, message: (value: T) => string): void {
    this.busy.set(true);
    this.error.set(null);
    request.subscribe({
      next: (value) => {
        this.busy.set(false);
        this.notice.set(message(value));
        this.reload();
      },
      error: (e: unknown) => {
        this.busy.set(false);
        this.error.set(ApiError.from(e).message);
      },
    });
  }
}
