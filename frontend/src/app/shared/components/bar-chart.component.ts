import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { SolesPipe } from '../pipes/soles.pipe';

export interface BarPoint {
  label: string;
  /** Texto corto para el eje (p. ej. día del mes). */
  tick: string;
  value: number;
  highlight?: boolean;
}

/**
 * Gráfico de barras ligero en SVG (sin librerías). Incluye una tabla oculta equivalente para
 * lectores de pantalla y descripciones nativas (`<title>`) en cada barra.
 */
@Component({
  selector: 'app-bar-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [SolesPipe],
  template: `
    <figure>
      <svg
        [attr.viewBox]="'0 0 ' + width + ' ' + height"
        class="h-44 w-full"
        role="img"
        [attr.aria-label]="summary()"
        preserveAspectRatio="none"
      >
        @if (variant() === 'area') {
          @for (y of [22, 62, 102, 142]; track y) {
            <line
              x1="0"
              [attr.y1]="y"
              [attr.x2]="width"
              [attr.y2]="y"
              stroke="#e4edf5"
              stroke-width="1"
            />
          }
          <polygon [attr.points]="areaPoints()" fill="#B3CFE5" fill-opacity="0.3" />
          <polyline
            [attr.points]="linePoints()"
            fill="none"
            stroke="#315f80"
            stroke-width="2.2"
            vector-effect="non-scaling-stroke"
          />
        }
        <line
          x1="0"
          [attr.y1]="height - 18"
          [attr.x2]="width"
          [attr.y2]="height - 18"
          stroke="#B3CFE5"
          stroke-width="1"
        />
        @for (bar of bars(); track bar.label) {
          <g>
            <title>{{ bar.label }}: {{ bar.value | soles }}</title>
            @if (variant() === 'bar') {
              <rect
                [attr.x]="bar.x"
                [attr.y]="bar.y"
                [attr.width]="bar.w"
                [attr.height]="bar.h"
                rx="2"
                [attr.fill]="bar.highlight ? '#0A1931' : '#4A7EA7'"
                [attr.fill-opacity]="bar.value > 0 ? 1 : 0.25"
              />
            }
            @if (bar.showTick) {
              <text
                [attr.x]="bar.x + bar.w / 2"
                [attr.y]="height - 4"
                text-anchor="middle"
                font-size="10"
                fill="#3B6A8F"
              >
                {{ bar.tick }}
              </text>
            }
          </g>
        }
      </svg>
      <figcaption class="sr-only">{{ summary() }}</figcaption>
      <div class="sr-only">
        <table>
          <caption>
            {{
              caption()
            }}
          </caption>
          <tbody>
            @for (p of points(); track p.label) {
              <tr>
                <th scope="row">{{ p.label }}</th>
                <td>{{ p.value | soles }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    </figure>
  `,
})
export class BarChartComponent {
  readonly points = input.required<BarPoint[]>();
  readonly caption = input('Ingresos confirmados por día');
  readonly variant = input<'bar' | 'area'>('bar');

  protected readonly width = 600;
  protected readonly height = 176;
  protected readonly linePoints = computed(() => {
    const points = this.points();
    const max = Math.max(...points.map((p) => p.value), 1);
    return points
      .map(
        (p, i) =>
          `${points.length > 1 ? (i * this.width) / (points.length - 1) : this.width / 2},${this.height - 18 - (p.value / max) * (this.height - 30)}`,
      )
      .join(' ');
  });
  protected readonly areaPoints = computed(
    () => `0,${this.height - 18} ${this.linePoints()} ${this.width},${this.height - 18}`,
  );

  protected readonly bars = computed(() => {
    const points = this.points();
    const max = Math.max(...points.map((p) => p.value), 1);
    const plot = this.height - 26;
    const step = this.width / Math.max(points.length, 1);
    const tickEvery = Math.ceil(points.length / 8);
    return points.map((p, i) => {
      const h = p.value > 0 ? Math.max((p.value / max) * plot, 3) : 2;
      return {
        ...p,
        x: i * step + step * 0.15,
        w: step * 0.7,
        h,
        y: this.height - 18 - h,
        showTick: i % tickEvery === 0 || i === points.length - 1,
      };
    });
  });

  protected readonly summary = computed(() => {
    const points = this.points();
    const total = points.reduce((sum, p) => sum + p.value, 0);
    const best = points.reduce(
      (a, b) => (b.value > a.value ? b : a),
      points[0] ?? { label: '', value: 0 },
    );
    return (
      `${this.caption()}: total S/ ${total.toFixed(2)} en ${points.length} días` +
      (best.value > 0 ? `; mejor día ${best.label}` : '')
    );
  });
}
