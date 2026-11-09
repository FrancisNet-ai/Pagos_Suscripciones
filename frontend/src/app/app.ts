import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  NavigationCancel,
  NavigationEnd,
  NavigationError,
  NavigationSkipped,
  NavigationStart,
  Router,
  RouterOutlet,
} from '@angular/router';
import { filter, map, of, switchMap, timer } from 'rxjs';
import { LoadingIndicatorComponent } from './shared/components/loading-indicator.component';

@Component({
  selector: 'app-root',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, LoadingIndicatorComponent],
  template: `
    <div [attr.inert]="navigating() ? '' : null" [attr.aria-busy]="navigating()">
      <router-outlet />
    </div>
    @if (navigating()) {
      <app-loading-indicator class="app-loading-screen" label="Cargando página…" />
    }
  `,
})
export class App {
  /** Las rutas rápidas no parpadean; terminar, cancelar o fallar retira el indicador de inmediato. */
  protected readonly navigating = toSignal(
    inject(Router).events.pipe(
      filter(
        (event) =>
          event instanceof NavigationStart ||
          event instanceof NavigationEnd ||
          event instanceof NavigationCancel ||
          event instanceof NavigationError ||
          event instanceof NavigationSkipped,
      ),
      switchMap((event) =>
        event instanceof NavigationStart ? timer(150).pipe(map(() => true)) : of(false),
      ),
    ),
    { initialValue: false },
  );
}
