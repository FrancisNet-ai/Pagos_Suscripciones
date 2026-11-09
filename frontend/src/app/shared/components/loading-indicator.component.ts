import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Solo el monograma es visible; el estado se anuncia a los lectores de pantalla. */
@Component({
  selector: 'app-loading-indicator',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'loading-indicator',
    role: 'status',
    'aria-live': 'polite',
    'aria-busy': 'true',
    '[attr.aria-label]': 'label()',
  },
  template:
    '<img class="loading-indicator__mark" src="loading-mark.svg" width="86" height="86" alt="" aria-hidden="true" />',
})
export class LoadingIndicatorComponent {
  readonly label = input('Cargando…');
}
