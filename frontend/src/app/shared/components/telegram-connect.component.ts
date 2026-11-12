import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { ApiError } from '../../core/services/api-error';

interface TelegramStatus {
  available: boolean;
  linked: boolean;
  botUsername: string | null;
}

/**
 * Conexión con el bot de avisos de Telegram. Si la plataforma no tiene el bot configurado, no se muestra.
 * Conectar abre Telegram con un código de un solo uso; la persona solo toca «Iniciar».
 */
@Component({
  selector: 'app-telegram-connect',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (status(); as s) {
      @if (s.available) {
        <section class="telegram-card" aria-labelledby="telegram-titulo">
          <div class="telegram-head">
            <svg viewBox="0 0 24 24" aria-hidden="true" class="telegram-icon"><path d="M21.5 4.5 2.9 11.7c-.8.3-.8 1.4 0 1.7l4.6 1.5 1.8 5.5c.2.7 1.1.9 1.6.4l2.6-2.5 4.6 3.4c.6.4 1.4.1 1.6-.6L22.8 5.8c.2-.9-.6-1.6-1.3-1.3Z" /><path d="m7.5 14.9 10.2-6.6-7.7 8.1" /></svg>
            <div>
              <h2 id="telegram-titulo" class="text-lg font-semibold text-night">Avisos por Telegram</h2>
              <p class="mt-1 text-sm">{{ description() }}</p>
            </div>
          </div>
          @if (s.linked) {
            <p class="notice notice-success mt-4" role="status">✅ Conectado a &#64;{{ s.botUsername }}. Te llegarán los avisos al instante.</p>
            <button type="button" class="btn btn-secondary mt-3" [disabled]="busy()" (click)="unlink()">Desconectar</button>
          } @else {
            <ol class="mt-4 grid gap-1 text-sm">
              <li>1. Toca «Conectar Telegram»: se abrirá el chat de &#64;{{ s.botUsername }}.</li>
              <li>2. Toca «Iniciar». Listo, el bot te confirmará la conexión.</li>
            </ol>
            <div class="mt-4 flex flex-wrap items-center gap-3">
              <button type="button" class="btn btn-primary" [disabled]="busy()" (click)="connect()">Conectar Telegram</button>
              @if (opened()) { <button type="button" class="btn btn-secondary" (click)="refresh()">Ya lo conecté</button> }
            </div>
            @if (link(); as l) {
              <p class="mt-3 text-sm">Si Telegram no se abrió, <a class="link" [href]="l" target="_blank" rel="noopener noreferrer">toca aquí</a>. El enlace vence en 15 minutos.</p>
            }
          }
          @if (error(); as e) { <p class="notice notice-error mt-3" role="alert">{{ e }}</p> }
        </section>
      }
    }
  `,
  styles: `
    .telegram-card { border: 1px solid var(--line-subtle); border-radius: var(--radius-panel); background: #fff; padding: 20px; }
    .telegram-head { display: flex; gap: 14px; align-items: flex-start; }
    .telegram-icon { flex: none; width: 36px; height: 36px; padding: 7px; border-radius: 999px; background: var(--color-corporate);
      fill: none; stroke: var(--color-snow); stroke-width: 1.6; stroke-linejoin: round; stroke-linecap: round; }
  `,
})
export class TelegramConnectComponent {
  private readonly http = inject(HttpClient);
  /** Qué avisos recibirá esta persona, para explicarlo en la tarjeta. */
  readonly description = input('Recibe en tu celular los pedidos nuevos y los pagos por revisar, sin abrir Nuvyra.');

  protected readonly status = signal<TelegramStatus | null>(null);
  protected readonly busy = signal(false);
  protected readonly opened = signal(false);
  protected readonly link = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  constructor() {
    this.refresh();
  }

  protected refresh(): void {
    this.http.get<TelegramStatus>('/api/v1/telegram').subscribe({
      next: (s) => {
        this.status.set(s);
        if (s.linked) this.link.set(null);
      },
      // Sin el bot (o sin respuesta) la tarjeta no aparece: no es una función crítica.
      error: () => this.status.set(null),
    });
  }

  protected connect(): void {
    this.busy.set(true);
    this.error.set(null);
    this.http.post<{ url: string }>('/api/v1/telegram/link', {}).subscribe({
      next: ({ url }) => {
        this.busy.set(false);
        this.link.set(url);
        this.opened.set(true);
        window.location.href = url;
      },
      error: (e: unknown) => {
        this.busy.set(false);
        this.error.set(ApiError.from(e).message);
      },
    });
  }

  protected unlink(): void {
    this.busy.set(true);
    this.http.delete<void>('/api/v1/telegram/link').subscribe({
      next: () => {
        this.busy.set(false);
        this.opened.set(false);
        this.refresh();
      },
      error: (e: unknown) => {
        this.busy.set(false);
        this.error.set(ApiError.from(e).message);
      },
    });
  }
}
