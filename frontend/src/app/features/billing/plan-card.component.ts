import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { SolesPipe } from '../../shared/pipes/soles.pipe';
import { Plan, TRIAL_DAYS, periodSuffix, planBenefits } from './billing.api';

/**
 * Tarjeta de un plan: nombre, precio, una frase y lo que incluye (con lo que no incluye tachado, para comparar).
 * `featured` la destaca en azul noche. Las acciones (elegir, registrarse) se proyectan desde quien la usa.
 * En celular muestra solo los primeros beneficios y el resto con «Más detalles», para que la tarjeta quepa en pantalla.
 */
let nextId = 0;

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
    @if (trial() && plan().price > 0) { <p class="plan-card__trial">{{ trialDays }} días gratis para probar</p> }
    @if (plan().description) { <p class="plan-card__description">{{ plan().description }}</p> }
    <ul class="plan-card__benefits" [id]="listId">
      @for (b of benefits(); track b.text; let i = $index) {
        <li [class.plan-card__missing]="!b.included" [class.plan-card__extra]="i >= visibleOnPhone && !expanded()">
          <span class="plan-card__mark" aria-hidden="true">{{ b.included ? '✓' : '—' }}</span>
          @if (b.included) { {{ b.text }} } @else { <span class="sr-only">No incluye: </span><s>{{ b.text }}</s> }
        </li>
      }
    </ul>
    @if (benefits().length > visibleOnPhone) {
      <button type="button" class="plan-card__more" [attr.aria-expanded]="expanded()" [attr.aria-controls]="listId"
              (click)="expanded.set(!expanded())">
        {{ expanded() ? 'Menos detalles' : 'Más detalles' }} <span aria-hidden="true" class="plan-card__chevron">⌄</span>
      </button>
    }
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
  /** Muestra «14 días gratis para probar» (solo en planes de pago). */
  readonly trial = input(false);
  protected readonly trialDays = TRIAL_DAYS;

  /** Beneficios incluidos primero; los que no incluye, al final. */
  protected readonly benefits = computed(() => {
    const all = planBenefits(this.plan());
    return [...all.filter((b) => b.included), ...all.filter((b) => !b.included)];
  });
  protected readonly visibleOnPhone = 3;
  protected readonly expanded = signal(false);
  protected readonly listId = `plan-beneficios-${nextId++}`;
  protected readonly suffix = computed(() => periodSuffix(this.plan().billingPeriod));
  protected readonly badge = computed(() => this.label() ?? (this.current() ? 'Tu plan' : null));
}
