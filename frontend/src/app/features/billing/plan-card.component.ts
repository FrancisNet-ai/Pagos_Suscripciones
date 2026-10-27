import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { SolesPipe } from '../../shared/pipes/soles.pipe';
import { Plan, periodSuffix, planLines } from './billing.api';

/**
 * Columna editorial de un plan: nombre, precio y lo que incluye, separada por un filete.
 * Las acciones (elegir, registrarse) se proyectan desde quien la usa.
 */
@Component({
  selector: 'app-plan-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [SolesPipe],
  host: { class: 'flex flex-col border-t-2 pt-6', '[class]': 'hostClass()' },
  template: `
    <div class="flex items-baseline justify-between gap-3">
      <h3 class="font-[family-name:var(--font-editorial)] text-2xl font-semibold" [class]="dark() ? 'text-snow' : 'text-night'">{{ plan().name }}</h3>
      @if (current()) {
        <span class="text-xs font-semibold uppercase tracking-[0.12em]" [class]="dark() ? 'text-mist' : 'text-steel'">Tu plan</span>
      }
    </div>
    <p class="mt-4 flex items-baseline gap-2">
      <span class="type-numeric font-[family-name:var(--font-display)] text-4xl" [class]="dark() ? 'text-snow' : 'text-night'">
        {{ plan().price === 0 ? 'Gratis' : (plan().price | soles) }}
      </span>
      @if (plan().price > 0) {
        <span class="text-sm" [class]="dark() ? 'text-mist' : 'text-[var(--text-muted)]'">{{ suffix() }}</span>
      }
    </p>
    @if (plan().description) {
      <p class="mt-3 text-[0.9375rem]" [class]="dark() ? 'text-mist' : ''">{{ plan().description }}</p>
    }
    <ul class="mt-5 grid gap-2 text-[0.9375rem]" [class]="dark() ? 'text-snow/90' : 'text-corporate'">
      @for (line of lines(); track line) {
        <li class="flex gap-2"><span aria-hidden="true" [class]="dark() ? 'text-mist' : 'text-steel'">—</span>{{ line }}</li>
      }
    </ul>
    <div class="mt-auto pt-6"><ng-content /></div>
  `,
})
export class PlanCardComponent {
  readonly plan = input.required<Plan>();
  readonly dark = input(false);
  readonly current = input(false);
  readonly selected = input(false);

  protected readonly lines = computed(() => planLines(this.plan()));
  protected readonly suffix = computed(() => periodSuffix(this.plan().billingPeriod));
  protected readonly hostClass = computed(() => {
    if (this.dark()) return this.selected() ? 'border-snow' : 'border-mist/30';
    return this.selected() || this.current() ? 'border-night' : 'border-[var(--line-subtle)]';
  });
}
