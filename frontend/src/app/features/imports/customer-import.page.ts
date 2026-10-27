import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiError } from '../../core/services/api-error';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { SolesPipe } from '../../shared/pipes/soles.pipe';
import { CustomerImport, ImportRow, ImportsApi, TEMPLATE_CSV } from './imports.api';

const MAX_BYTES = 2 * 1024 * 1024;
type Filter = 'ALL' | 'OK' | 'ERROR';

@Component({
  selector: 'app-customer-import-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, SolesPipe, StatusBadgeComponent],
  template: `
    <section class="mx-auto max-w-6xl" aria-labelledby="importar-titulo">
      <a routerLink="/panel/clientes" class="text-sm text-corporate no-underline hover:underline">← Clientes</a>
      <p class="type-eyebrow mt-4">Clientes</p>
      <h1 id="importar-titulo" class="type-h1 mt-3">Importar clientes</h1>
      <p class="mt-3 max-w-2xl">
        Sube los clientes que ya tenías desde un archivo CSV o Excel (.xlsx). Primero verás una vista previa: no se crea nada hasta que confirmes.
        Las suscripciones importadas no cuentan como ventas nuevas y ocupan cupo si están activas.
      </p>

      @if (error(); as e) { <p class="notice notice-error mt-6" role="alert">{{ e }}</p> }

      @if (!data()) {
        <div class="mt-10 grid gap-10 lg:grid-cols-[1.2fr_1fr]">
          <div class="rounded-[var(--radius-panel)] border-2 border-dashed border-[var(--line-subtle)] p-8 text-center"
               (dragover)="$event.preventDefault()" (drop)="onDrop($event)">
            <p class="font-[family-name:var(--font-editorial)] text-2xl text-night">Arrastra tu archivo aquí</p>
            <p class="mt-2 text-sm">CSV o Excel (.xlsx), hasta 2 MB y 2000 filas.</p>
            <label for="import-file" class="btn btn-primary mt-6 cursor-pointer">
              @if (busy()) { <span class="spinner" aria-hidden="true"></span> Analizando… } @else { Elegir archivo }
            </label>
            <input id="import-file" type="file" class="sr-only" accept=".csv,.xlsx,text/csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" (change)="onFile($event)" [disabled]="busy()" />
          </div>
          <div>
            <h2 class="font-[family-name:var(--font-editorial)] text-xl font-semibold text-night">Columnas</h2>
            <dl class="mt-4 grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-sm">
              <dt class="font-semibold text-night">nombre *</dt><dd>Nombre del cliente</dd>
              <dt class="font-semibold text-night">celular *</dt><dd>Celular peruano de 9 dígitos</dd>
              <dt class="font-semibold text-night">producto</dt><dd>Nombre exacto de un producto tuyo; vacío si no tiene suscripción</dd>
              <dt class="font-semibold text-night">inicio</dt><dd>Fecha de inicio (dd/mm/aaaa)</dd>
              <dt class="font-semibold text-night">duracion_dias</dt><dd>O la columna «vencimiento» con una fecha; si faltan, se usa la del producto</dd>
              <dt class="font-semibold text-night">precio</dt><dd>Lo que pagó; si falta, el precio actual del producto</dd>
              <dt class="font-semibold text-night">estado</dt><dd>activa, vencida o cancelada; si falta, se calcula con las fechas</dd>
              <dt class="font-semibold text-night">notas</dt><dd>Opcional</dd>
            </dl>
            <button type="button" class="btn btn-secondary mt-6" (click)="downloadTemplate()">Descargar plantilla</button>
            <p class="mt-4 text-xs text-[var(--text-muted)]">Los clientes importados no tienen autorización de recordatorios registrada: sus avisos no se envían automáticamente.</p>
          </div>
        </div>
      } @else {
        @let d = data()!;
        <div class="mt-10 flex flex-wrap items-baseline justify-between gap-4 border-b border-[var(--line-subtle)] pb-6">
          <div>
            <p class="text-sm text-[var(--text-muted)]">{{ d.fileName }} · {{ d.totalRows }} {{ d.totalRows === 1 ? 'fila' : 'filas' }}</p>
            @if (d.status === 'COMMITTED' && d.result; as r) {
              <p class="mt-2 font-[family-name:var(--font-editorial)] text-2xl text-night">
                Importación aplicada: {{ r.created }} {{ r.created === 1 ? 'cliente nuevo' : 'clientes nuevos' }} y {{ r.subscriptionsAdded }} {{ r.subscriptionsAdded === 1 ? 'suscripción' : 'suscripciones' }}.
              </p>
              @if (r.failed > 0) { <p class="mt-1 text-sm text-danger">{{ r.failed }} {{ r.failed === 1 ? 'fila no se pudo aplicar' : 'filas no se pudieron aplicar' }}; revisa el motivo abajo.</p> }
            } @else {
              <p class="mt-2 font-[family-name:var(--font-editorial)] text-2xl text-night">
                {{ d.summary.toCreate }} nuevos · {{ d.summary.existing }} ya existentes · {{ d.summary.withSubscription }} con suscripción
              </p>
              @if (d.summary.errors > 0) { <p class="mt-1 text-sm text-danger">{{ d.summary.errors }} {{ d.summary.errors === 1 ? 'fila tiene errores y no se importará' : 'filas tienen errores y no se importarán' }}.</p> }
            }
          </div>
          <div class="flex flex-wrap gap-3">
            @if (d.status === 'PREVIEW') {
              <button type="button" class="btn btn-primary" [disabled]="busy() || d.totalRows === d.summary.errors" (click)="commit(d)">
                @if (busy()) { <span class="spinner" aria-hidden="true"></span> Importando… } @else { Confirmar importación }
              </button>
              <button type="button" class="btn btn-secondary" [disabled]="busy()" (click)="discard(d)">Descartar</button>
            } @else {
              <a class="btn btn-primary" routerLink="/panel/clientes">Ver clientes</a>
              <button type="button" class="btn btn-secondary" (click)="reset()">Importar otro archivo</button>
            }
          </div>
        </div>

        <div class="mt-4 flex gap-1" role="group" aria-label="Filtrar filas">
          @for (f of filters; track f.value) {
            <button type="button" class="rounded-full px-4 py-2 text-sm font-semibold" [class]="filter() === f.value ? 'bg-night text-snow' : 'text-corporate hover:bg-mist/30'"
                    [attr.aria-pressed]="filter() === f.value" (click)="filter.set(f.value)">{{ f.label }}</button>
          }
        </div>

        <ul class="mt-4 divide-y divide-[var(--line-subtle)] border-y border-[var(--line-subtle)]">
          @for (r of visibleRows(); track r.rowNumber) {
            <li class="grid gap-2 py-4 sm:grid-cols-[4rem_1.2fr_1.5fr_auto] sm:items-start sm:gap-6">
              <span class="type-numeric text-sm text-[var(--text-muted)]">Fila {{ r.rowNumber }}</span>
              <div class="min-w-0">
                <p class="font-semibold text-night">{{ r.fullName ?? '—' }}</p>
                <p class="type-numeric text-sm text-[var(--text-muted)]">{{ local(r.phone) }}</p>
              </div>
              <div class="text-sm">
                @if (r.errors.length > 0) {
                  <ul class="grid gap-1 text-danger">@for (e of r.errors; track e) { <li>{{ e }}</li> }</ul>
                } @else if (r.productName) {
                  <p class="text-night">{{ r.productName }} · {{ r.price | soles }}</p>
                  <p class="text-[var(--text-muted)]">Desde {{ date(r.startDate) }} · {{ r.durationDays }} días · {{ statusLabel(r) }}</p>
                } @else {
                  <p class="text-[var(--text-muted)]">Sin suscripción</p>
                }
                @if (r.outcomeMessage) { <p class="mt-1 text-danger">{{ r.outcomeMessage }}</p> }
              </div>
              <app-status-badge [label]="badge(r).label" [tone]="badge(r).tone" />
            </li>
          }
        </ul>
      }
    </section>
  `,
})
export class CustomerImportPage {
  private readonly api = inject(ImportsApi);
  protected readonly data = signal<CustomerImport | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly filter = signal<Filter>('ALL');
  protected readonly filters: { value: Filter; label: string }[] = [
    { value: 'ALL', label: 'Todas' },
    { value: 'OK', label: 'Se importan' },
    { value: 'ERROR', label: 'Con errores' },
  ];

  protected readonly visibleRows = computed(() => {
    const rows = this.data()?.rows ?? [];
    const f = this.filter();
    return f === 'ALL' ? rows : rows.filter((r) => (f === 'ERROR') === (r.action === 'ERROR'));
  });

  protected onFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (file) this.upload(file);
  }

  protected onDrop(event: DragEvent): void {
    event.preventDefault();
    const file = event.dataTransfer?.files?.[0];
    if (file) this.upload(file);
  }

  protected commit(d: CustomerImport): void {
    this.run(this.api.commit(d.id));
  }

  protected discard(d: CustomerImport): void {
    this.api.discard(d.id).subscribe({ next: () => this.reset(), error: (e: unknown) => this.error.set(ApiError.from(e).message) });
  }

  protected reset(): void {
    this.data.set(null);
    this.error.set(null);
    this.filter.set('ALL');
  }

  protected downloadTemplate(): void {
    const url = URL.createObjectURL(new Blob([TEMPLATE_CSV], { type: 'text/csv;charset=utf-8' }));
    const a = document.createElement('a');
    a.href = url;
    a.download = 'plantilla-clientes.csv';
    a.click();
    URL.revokeObjectURL(url);
  }

  protected badge(r: ImportRow): { label: string; tone: 'success' | 'warning' | 'neutral' | 'danger' | 'info' } {
    switch (r.outcome) {
      case 'CREATED': return { label: 'Creado', tone: 'success' };
      case 'SUBSCRIPTION_ADDED': return { label: 'Suscripción agregada', tone: 'success' };
      case 'UNCHANGED': return { label: 'Ya existía', tone: 'neutral' };
      case 'FAILED': return { label: 'No aplicado', tone: 'danger' };
      case 'SKIPPED': return { label: 'Omitido', tone: 'neutral' };
    }
    return { CREATE: { label: 'Nuevo', tone: 'info' as const }, EXISTING: { label: 'Ya existe', tone: 'neutral' as const }, ERROR: { label: 'Con errores', tone: 'danger' as const } }[r.action];
  }

  protected statusLabel(r: ImportRow): string {
    return { ACTIVE: 'activa', EXPIRED: 'vencida', CANCELLED: 'cancelada' }[r.status ?? 'ACTIVE'];
  }

  protected local(e164: string | null): string {
    if (!e164) return '';
    const n = e164.replace(/^\+51/, '');
    return `${n.slice(0, 3)} ${n.slice(3, 6)} ${n.slice(6)}`;
  }

  protected date(iso: string | null): string {
    return iso ? new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium', timeZone: 'UTC' }).format(new Date(iso)) : '';
  }

  private upload(file: File): void {
    if (file.size > MAX_BYTES) {
      this.error.set('El archivo supera los 2 MB.');
      return;
    }
    this.run(this.api.preview(file));
  }

  private run(request: ReturnType<ImportsApi['preview']>): void {
    this.busy.set(true);
    this.error.set(null);
    request.subscribe({
      next: (d) => {
        this.data.set(d);
        this.busy.set(false);
      },
      error: (e: unknown) => {
        this.error.set(ApiError.from(e).message);
        this.busy.set(false);
      },
    });
  }
}
