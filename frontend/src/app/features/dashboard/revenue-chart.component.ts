import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { SolesPipe } from '../../shared/pipes/soles.pipe';
import { DayAmount } from '../reports/reports.api';

const DATE = new Intl.DateTimeFormat('es-PE', { day: 'numeric', month: 'short', timeZone: 'UTC' });
const FULL_DATE = new Intl.DateTimeFormat('es-PE', {
  day: 'numeric',
  month: 'long',
  year: 'numeric',
  timeZone: 'UTC',
});

/** Importes diarios de la API. SVG ligero, consulta táctil/teclado y tabla equivalente. */
@Component({
  selector: 'app-revenue-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [SolesPipe],
  template: `
    <div class="revenue-total">
      <strong>{{ total() | soles }}</strong>
      <span
        >{{ payments() }} {{ payments() === 1 ? 'cobro verificado' : 'cobros verificados' }} en el
        periodo</span
      >
    </div>
    <figure>
      @if (total() > 0) {
        <div class="revenue-plot">
          <div class="revenue-axis" aria-hidden="true">
            @for (value of ticks(); track $index) {
              <span>{{ value | soles }}</span>
            }
          </div>
          <div class="revenue-canvas">
            <div class="revenue-drawing">
              <svg
                viewBox="0 0 600 200"
                preserveAspectRatio="none"
                role="img"
                [attr.aria-label]="'Ingresos confirmados por día. Total: ' + (total() | soles)"
                (pointermove)="inspect($event)"
                (pointerdown)="inspect($event)"
              >
                @for (y of [8, 53, 98, 143, 188]; track y) {
                  <line
                    x1="0"
                    [attr.y1]="y"
                    x2="600"
                    [attr.y2]="y"
                    stroke="#e4edf5"
                    stroke-width="1"
                  />
                }
                <polygon
                  [attr.points]="'0,188 ' + line() + ' 600,188'"
                  fill="#B3CFE5"
                  fill-opacity="0.3"
                />
                <polyline
                  [attr.points]="line()"
                  fill="none"
                  stroke="#1A3D63"
                  stroke-width="2.5"
                  vector-effect="non-scaling-stroke"
                />
                @if (selected(); as point) {
                  <line
                    [attr.x1]="x(selectedIndex())"
                    y1="8"
                    [attr.x2]="x(selectedIndex())"
                    y2="188"
                    stroke="#4A7EA7"
                    stroke-opacity="0.55"
                    stroke-dasharray="3 5"
                    vector-effect="non-scaling-stroke"
                  />
                }
              </svg>
              @if (selected(); as point) {
                <span
                  class="revenue-dot"
                  aria-hidden="true"
                  [style.left.%]="x(selectedIndex()) / 6"
                  [style.top.%]="y(point.amount) / 2"
                ></span>
              }
            </div>
            <div class="revenue-dates" aria-hidden="true">
              @for (date of dateTicks(); track $index) {
                <span>{{ shortDate(date) }}</span>
              }
            </div>
          </div>
        </div>
      } @else {
        <div class="revenue-empty">
          <span class="revenue-empty-line" aria-hidden="true"></span>
          <p>Aún no hay cobros verificados en este periodo.</p>
          <p>Cuando confirmes un pago, aparecerá aquí.</p>
        </div>
      }
      <figcaption class="sr-only">Importes diarios en soles · solo pagos verificados</figcaption>
    </figure>
    @if (selected(); as point) {
      <div class="revenue-selection" aria-live="polite" aria-atomic="true">
        <div>
          <time [attr.datetime]="point.date">{{ fullDate(point.date) }}</time>
          <span
            >{{ point.count }}
            {{ point.count === 1 ? 'cobro verificado' : 'cobros verificados' }}</span
          >
        </div>
        <strong>{{ point.amount | soles }}</strong>
      </div>
      <label class="revenue-day-picker">
        <span>Consultar día</span>
        <input
          type="range"
          min="0"
          [max]="days().length - 1"
          step="1"
          [value]="selectedIndex()"
          aria-label="Elegir día de ingresos"
          [attr.aria-valuetext]="
            fullDate(point.date) + ': ' + (point.amount | soles) + ', ' + point.count + ' cobros'
          "
          (input)="choose($event)"
        />
      </label>
    }
    <details class="revenue-data">
      <summary>Ver datos del gráfico</summary>
      <table>
        <caption class="sr-only">
          Ingresos confirmados del periodo seleccionado
        </caption>
        <thead>
          <tr>
            <th scope="col">Fecha</th>
            <th scope="col">Cobros</th>
            <th scope="col">Importe</th>
          </tr>
        </thead>
        <tbody>
          @for (point of days(); track point.date) {
            <tr>
              <th scope="row">{{ shortDate(point.date) }}</th>
              <td>{{ point.count }}</td>
              <td>{{ point.amount | soles }}</td>
            </tr>
          }
        </tbody>
      </table>
    </details>
  `,
  styleUrl: './revenue-chart.component.css',
})
export class RevenueChartComponent {
  readonly days = input.required<DayAmount[]>();
  private readonly picked = signal<number | null>(null);
  protected readonly selectedIndex = computed(() =>
    Math.max(0, Math.min(this.picked() ?? this.days().length - 1, this.days().length - 1)),
  );
  protected readonly selected = computed(() => this.days()[this.selectedIndex()] ?? null);
  protected readonly total = computed(
    () => this.days().reduce((sum, day) => sum + Math.round(day.amount * 100), 0) / 100,
  );
  protected readonly payments = computed(() =>
    this.days().reduce((sum, day) => sum + day.count, 0),
  );
  protected readonly maximum = computed(() => {
    const max = Math.max(...this.days().map((day) => day.amount), 1);
    const magnitude = 10 ** Math.floor(Math.log10(max));
    return Math.ceil(max / magnitude) * magnitude;
  });
  protected readonly ticks = computed(() =>
    [1, 0.75, 0.5, 0.25, 0].map((part) => this.maximum() * part),
  );
  protected readonly dateTicks = computed(() => {
    const days = this.days();
    return [0, 0.25, 0.5, 0.75, 1].map(
      (part) => days[Math.round(part * (days.length - 1))]?.date ?? '',
    );
  });
  protected readonly line = computed(() =>
    this.days()
      .map((point, i) => `${this.x(i)},${this.y(point.amount)}`)
      .join(' '),
  );
  protected x(index: number): number {
    return this.days().length > 1 ? (index / (this.days().length - 1)) * 600 : 300;
  }
  protected y(amount: number): number {
    return 188 - (amount / this.maximum()) * 180;
  }
  protected shortDate(date: string): string {
    return date ? DATE.format(new Date(date + 'T12:00:00Z')) : '';
  }
  protected fullDate(date: string): string {
    return FULL_DATE.format(new Date(date + 'T12:00:00Z'));
  }
  protected choose(event: Event): void {
    this.picked.set(Number((event.target as HTMLInputElement).value));
  }
  protected inspect(event: PointerEvent): void {
    const rect = (event.currentTarget as SVGElement).getBoundingClientRect();
    if (rect.width > 0)
      this.picked.set(
        Math.max(
          0,
          Math.min(
            this.days().length - 1,
            Math.round(((event.clientX - rect.left) / rect.width) * (this.days().length - 1)),
          ),
        ),
      );
  }
}
