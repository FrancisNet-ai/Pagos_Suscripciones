import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiError } from '../../core/services/api-error';
import { BarChartComponent, BarPoint } from '../../shared/components/bar-chart.component';
import { SolesPipe } from '../../shared/pipes/soles.pipe';
import { ReportsApi, SalesReport } from './reports.api';

type Preset = 'MONTH' | 'LAST_MONTH' | 'LAST_30' | 'CUSTOM';

const STATUS_LABEL: Record<string, string | undefined> = {
  PAID: 'Pagados',
  PAYMENT_REPORTED: 'Por verificar',
  AWAITING_PAYMENT: 'Pendientes de pago',
  CANCELLED: 'Cancelados',
  EXPIRED: 'Vencidos sin pago',
};

function iso(d: Date): string {
  const local = new Date(d.getTime() - d.getTimezoneOffset() * 60000);
  return local.toISOString().slice(0, 10);
}

@Component({
  selector: 'app-reports-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, SolesPipe, BarChartComponent],
  templateUrl: './reports.page.html',
})
export class ReportsPage {
  private readonly api = inject(ReportsApi);
  protected readonly statusLabel = STATUS_LABEL;
  protected readonly presets: { value: Preset; label: string }[] = [
    { value: 'MONTH', label: 'Este mes' },
    { value: 'LAST_MONTH', label: 'Mes anterior' },
    { value: 'LAST_30', label: 'Últimos 30 días' },
    { value: 'CUSTOM', label: 'Personalizado' },
  ];
  protected readonly preset = signal<Preset>('MONTH');
  protected readonly from = new FormControl('', { nonNullable: true });
  protected readonly to = new FormControl('', { nonNullable: true });
  protected readonly report = signal<SalesReport | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly chart = computed<BarPoint[]>(() =>
    (this.report()?.byDay ?? []).map((d) => ({
      label: new Intl.DateTimeFormat('es-PE', { day: 'numeric', month: 'long' }).format(new Date(`${d.date}T12:00:00`)),
      tick: d.date.slice(8, 10),
      value: Number(d.amount),
    })),
  );
  protected readonly maxProduct = computed(() => Math.max(...(this.report()?.byProduct ?? []).map((p) => Number(p.amount)), 1));

  constructor() {
    this.applyPreset('MONTH');
  }

  protected applyPreset(p: Preset): void {
    this.preset.set(p);
    const now = new Date();
    if (p === 'MONTH') this.load(iso(new Date(now.getFullYear(), now.getMonth(), 1)), iso(now));
    if (p === 'LAST_MONTH') this.load(iso(new Date(now.getFullYear(), now.getMonth() - 1, 1)), iso(new Date(now.getFullYear(), now.getMonth(), 0)));
    if (p === 'LAST_30') this.load(iso(new Date(now.getTime() - 29 * 86400000)), iso(now));
  }

  protected applyCustom(): void {
    if (this.from.value && this.to.value) this.load(this.from.value, this.to.value);
  }

  protected range(r: SalesReport): string {
    const f = (d: string) => new Intl.DateTimeFormat('es-PE', { day: 'numeric', month: 'short', year: 'numeric' }).format(new Date(`${d}T12:00:00`));
    return `${f(r.from)} – ${f(r.to)}`;
  }

  private load(from: string, to: string): void {
    this.from.setValue(from);
    this.to.setValue(to);
    this.loading.set(true);
    this.error.set(null);
    this.api.sales(from, to).subscribe({
      next: (r) => {
        this.report.set(r);
        this.loading.set(false);
      },
      error: (e: unknown) => {
        this.error.set(ApiError.from(e).message);
        this.loading.set(false);
      },
    });
  }
}
