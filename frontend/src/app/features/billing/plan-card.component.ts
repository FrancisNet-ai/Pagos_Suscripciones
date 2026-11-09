import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { SolesPipe } from '../../shared/pipes/soles.pipe';
import { Plan, periodSuffix, planBenefits } from './billing.api';

/**
 * Tarjeta de un plan: nombre, precio, una frase y lo que incluye (con lo que no incluye tachado, para comparar).
 * `featured` la destaca en azul noche. Las acciones (elegir, registrarse) se proyectan desde quien la usa.
 */
@Component({
  selector: 'app-plan-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [SolesPipe],
  host: { class: 'plan-card', '[class.plan-card--featured]': 'featured()', '[class.plan-card--selected]': 'selected() || current()' },
  styleUrl: './plan-card.component.css',
  template: `
    @if (badge(); as b) { <p class="plan-card__badge">{{ b }}</p> }
    <h3 class="plan-card__name">{{ plan().name }}</h3>
    <p class="plan-card__price">
      <span class="type-numeric">{{ plan().price === 0 ? 'Gratis' : (plan().price | soles) }}</span>
      @if (plan().price > 0) { <small>{{ suffix() }}</small> }
    </p>
    @if (plan().description) { <p class="plan-card__description">{{ plan().description }}</p> }
    <ul class="plan-card__benefits">
      @for (b of benefits(); track b.text) {
        <li [class.plan-card__missing]="!b.included">
          <span class="plan-card__mark" aria-hidden="true">{{ b.included ? '✓' : '—' }}</span>
          @if (b.included) { {{ b.text }} } @else { <span class="sr-only">No incluye: </span><s>{{ b.text }}</s> }
        </li>
      }
    </ul>
    <div class="plan-card__action"><ng-content /></div>
  `,
})
export class PlanCardComponent {
  readonly plan = input.required<Plan>();
  /** Fondo azul noche para destacar el plan (recomendado, elegido o actual). */
  readonly featured = input(false);
  readonly current = input(false);
  readonly selected = input(false);
  /** Etiqueta corta sobre el nombre, p. ej. «Recomendado» o «Tu plan». */
  readonly label = input<string | null>(null);

  protected readonly benefits = computed(() => planBenefits(this.plan()));
  protected readonly suffix = computed(() => periodSuffix(this.plan().billingPeriod));
  protected readonly badge = computed(() => this.label() ?? (this.current() ? 'Tu plan' : null));
}
