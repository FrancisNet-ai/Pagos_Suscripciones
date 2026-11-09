import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Pasos de un proceso (p. ej. registro → plan → pago). `current` empieza en 1. */
@Component({
  selector: 'app-stepper',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ol class="stepper" [attr.aria-label]="'Paso ' + current() + ' de ' + steps().length">
      @for (s of steps(); track s; let i = $index) {
        <li [class.stepper__done]="i + 1 < current()" [class.stepper__current]="i + 1 === current()"
            [attr.aria-current]="i + 1 === current() ? 'step' : null">
          <span class="stepper__dot" aria-hidden="true">{{ i + 1 < current() ? '✓' : i + 1 }}</span>
          <span class="stepper__label">{{ s }}</span>
        </li>
      }
    </ol>
  `,
  styles: `
    .stepper { display: flex; align-items: flex-start; gap: 6px; }
    .stepper li { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 6px; position: relative;
      font-size: 0.75rem; color: var(--text-muted); text-align: center; }
    .stepper li:not(:last-child)::after { content: ''; position: absolute; top: 15px; left: calc(50% + 20px);
      right: calc(-50% + 20px); height: 2px; border-radius: 2px; background: var(--color-mist); }
    .stepper li.stepper__done:not(:last-child)::after { background: var(--color-steel); }
    .stepper__dot { width: 30px; height: 30px; border-radius: 999px; display: grid; place-items: center; font-weight: 600;
      background: white; border: 2px solid var(--color-mist); color: var(--color-steel); }
    .stepper__done .stepper__dot { background: var(--color-steel); border-color: var(--color-steel); color: var(--color-snow); }
    .stepper__current .stepper__dot { background: var(--color-night); border-color: var(--color-night); color: var(--color-snow); }
    .stepper__current { color: var(--color-night); font-weight: 600; }
  `,
})
export class StepperComponent {
  readonly steps = input.required<string[]>();
  readonly current = input.required<number>();
}
