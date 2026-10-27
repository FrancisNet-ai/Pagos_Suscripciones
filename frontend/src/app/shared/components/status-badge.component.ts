import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export type BadgeTone = 'success' | 'warning' | 'neutral' | 'danger' | 'info';

/** Indicador de estado discreto: punto de color + texto (el color nunca es la única señal). */
@Component({
  selector: 'app-status-badge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="inline-flex items-center gap-1.5 whitespace-nowrap text-[0.8125rem] font-semibold" [class]="toneClass()">
    <span class="h-1.5 w-1.5 rounded-full bg-current" aria-hidden="true"></span>{{ label() }}
  </span>`,
})
export class StatusBadgeComponent {
  readonly label = input.required<string>();
  readonly tone = input<BadgeTone>('neutral');
  protected readonly toneClass = computed(
    () =>
      ({
        success: 'text-success',
        warning: 'text-warning',
        danger: 'text-danger',
        info: 'text-corporate',
        neutral: 'text-[var(--text-muted)]',
      })[this.tone()],
  );
}
