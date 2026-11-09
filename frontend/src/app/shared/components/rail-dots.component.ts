import { ChangeDetectionStrategy, Component, computed, effect, input, signal } from '@angular/core';

/**
 * Puntos bajo una fila deslizable (● ○ ○): dicen cuántas tarjetas hay y cuál se ve. Tocar un punto lleva a esa
 * tarjeta. Solo se muestran en celular, donde la fila se desliza de lado.
 */
@Component({
  selector: 'app-rail-dots',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'rail-dots' },
  template: `
    @for (_ of items(); track $index; let i = $index) {
      <button type="button" class="rail-dots__dot" [class.rail-dots__dot--active]="i === active()"
              [attr.aria-label]="label() + ' ' + (i + 1) + ' de ' + count()" [attr.aria-current]="i === active() ? 'true' : null"
              (click)="go(i)"></button>
    }
  `,
  styles: `
    :host { display: flex; justify-content: center; gap: 2px; margin-top: 4px; }
    .rail-dots__dot { display: grid; place-items: center; width: 28px; height: 28px; border-radius: 999px; }
    .rail-dots__dot::before { content: ''; width: 8px; height: 8px; border-radius: 999px; background: var(--color-mist);
      transition: width 0.2s ease, background-color 0.2s ease; }
    .rail-dots__dot--active::before { width: 22px; background: var(--color-corporate); }
    .rail-dots__dot:focus-visible { outline: 2px solid var(--color-corporate); outline-offset: 1px; }
    @media (min-width: 768px) { :host { display: none; } }
  `,
})
export class RailDotsComponent {
  /** La fila deslizable (un elemento con `.plan-rail`). */
  readonly rail = input.required<HTMLElement>();
  readonly count = input.required<number>();
  /** Nombre de cada tarjeta para lectores de pantalla, p. ej. «Plan». */
  readonly label = input('Plan');

  protected readonly active = signal(0);
  protected readonly items = computed(() => Array.from({ length: this.count() }));

  constructor() {
    effect((onCleanup) => {
      const el = this.rail();
      const update = () => this.active.set(this.closestIndex(el));
      el.addEventListener('scroll', update, { passive: true });
      onCleanup(() => el.removeEventListener('scroll', update));
    });
  }

  protected go(index: number): void {
    const el = this.rail();
    const card = el.children.item(index) as HTMLElement | null;
    if (card) el.scrollTo({ left: card.offsetLeft - (el.clientWidth - card.offsetWidth) / 2, behavior: 'smooth' });
    this.active.set(index);
  }

  /** La tarjeta cuyo centro está más cerca del centro visible de la fila. */
  private closestIndex(el: HTMLElement): number {
    const center = el.scrollLeft + el.clientWidth / 2;
    let best = 0;
    let bestDistance = Infinity;
    Array.from(el.children).forEach((child, i) => {
      const c = child as HTMLElement;
      const distance = Math.abs(c.offsetLeft + c.offsetWidth / 2 - center);
      if (distance < bestDistance) {
        bestDistance = distance;
        best = i;
      }
    });
    return best;
  }
}
