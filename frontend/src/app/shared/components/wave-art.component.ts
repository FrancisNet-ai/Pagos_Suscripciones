import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Ilustración original de mármol fluido azul. Decorativa: oculta a lectores de pantalla. */
@Component({
  selector: 'app-wave-art',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'aria-hidden': 'true', class: 'pointer-events-none block' },
  template: `<div class="fluid-art" [style.opacity]="intensity()"></div>`,
})
export class WaveArtComponent {
  /** 0–1: opacidad global de la composición. */
  readonly intensity = input(1);
}
