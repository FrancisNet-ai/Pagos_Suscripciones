import { ChangeDetectionStrategy, Component } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { fromEvent, map, merge } from 'rxjs';

@Component({
  selector: 'app-connection-status',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (!online()) {
      <aside class="connection-notice notice notice-warning" role="status">
        Sin conexión. Vuelve a conectarte para cargar datos o guardar cambios.
      </aside>
    }
  `,
  styles: `
    .connection-notice {
      position: fixed;
      z-index: 1100;
      inset-inline: 1rem;
      bottom: max(1rem, env(safe-area-inset-bottom));
      max-inline-size: 34rem;
      margin-inline: auto;
      box-shadow: var(--shadow-hairline);
    }
  `,
})
export class ConnectionStatusComponent {
  protected readonly online = toSignal(
    merge(fromEvent(window, 'online'), fromEvent(window, 'offline')).pipe(
      map(() => navigator.onLine),
    ),
    { initialValue: navigator.onLine },
  );
}
