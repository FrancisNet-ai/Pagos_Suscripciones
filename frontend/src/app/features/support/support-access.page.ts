import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Observable } from 'rxjs';
import { ApiError } from '../../core/services/api-error';
import { formatDateTime } from '../orders/orders.api';
import { SupportAccess, SupportApi } from './support.api';

/** El titular decide si soporte puede consultar sus pedidos y clientes, por cuánto tiempo, y ve cada consulta. */
@Component({
  selector: 'app-support-access-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule],
  template: `
    <section class="grid gap-10">
      <div>
        <h2 class="font-[family-name:var(--font-editorial)] text-2xl font-semibold">Acceso de soporte</h2>
        <p class="mt-3">
          El equipo de Nuvyra no ve los datos de tus compradores. Si necesitas ayuda con un pedido o un cliente, puedes
          autorizar a soporte a consultarlos (solo lectura) por un tiempo limitado. Cada consulta queda registrada aquí con su motivo.
        </p>
      </div>

      @if (error(); as e) {
        <p class="notice notice-error" role="alert">{{ e }}</p>
      }

      @if (data(); as d) {
        @if (d.active; as a) {
          <div class="notice notice-warning flex-wrap items-center justify-between gap-x-6 gap-y-2" role="status">
            <span>Soporte puede consultar tus pedidos y clientes hasta el {{ date(a.expiresAt) }}.</span>
            <button type="button" class="btn btn-secondary !min-h-10 !text-xs" [disabled]="busy()" (click)="revoke()">Retirar acceso ahora</button>
          </div>
        } @else {
          <form class="grid gap-5" [formGroup]="form" (ngSubmit)="grant()">
            <div class="grid gap-5 sm:grid-cols-[12rem_1fr]">
              <div class="field">
                <label class="field-label" for="hours">Duración</label>
                <select id="hours" class="input" formControlName="hours">
                  <option [ngValue]="1">1 hora</option>
                  <option [ngValue]="4">4 horas</option>
                  <option [ngValue]="24">24 horas</option>
                  <option [ngValue]="72">3 días</option>
                </select>
              </div>
              <div class="field">
                <label class="field-label" for="note">Qué debe revisar (opcional)</label>
                <input id="note" class="input" formControlName="note" maxlength="300" placeholder="Ej.: el pedido ORD-2026-000123 aparece duplicado" />
              </div>
            </div>
            <div>
              <button type="submit" class="btn btn-primary" [disabled]="busy()">Autorizar a soporte</button>
            </div>
          </form>
        }

        <section aria-labelledby="consultas-titulo">
          <h3 id="consultas-titulo" class="font-[family-name:var(--font-editorial)] text-xl font-semibold">Consultas de soporte</h3>
          @if (d.views.length === 0) {
            <p class="mt-3 text-sm text-[var(--text-muted)]">Soporte no ha consultado tus datos.</p>
          } @else {
            <ul class="mt-3 divide-y divide-[var(--line-subtle)] border-y border-[var(--line-subtle)] text-sm">
              @for (v of d.views; track $index) {
                <li class="grid gap-1 py-3 sm:grid-cols-[11rem_7rem_1fr]">
                  <span class="text-[var(--text-muted)]">{{ date(v.at) }}</span>
                  <span class="font-semibold text-night">{{ v.resource }}</span>
                  <span>{{ v.reason }}</span>
                </li>
              }
            </ul>
          }
        </section>
      } @else if (!error()) {
        <div class="h-40 animate-pulse rounded bg-mist/25" aria-busy="true"></div>
      }
    </section>
  `,
})
export class SupportAccessPage {
  private readonly api = inject(SupportApi);
  protected readonly data = signal<SupportAccess | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly hours = new FormControl(4, { nonNullable: true });
  protected readonly note = new FormControl('', { nonNullable: true, validators: [Validators.maxLength(300)] });
  protected readonly form = new FormGroup({ hours: this.hours, note: this.note });
  protected readonly date = formatDateTime;

  constructor() {
    this.run(this.api.status());
  }

  protected grant(): void {
    this.run(this.api.grant(this.hours.value, this.note.value.trim() || null));
  }

  protected revoke(): void {
    this.run(this.api.revoke());
  }

  private run(request: Observable<SupportAccess>): void {
    this.busy.set(true);
    this.error.set(null);
    request.subscribe({
      next: (d) => {
        this.data.set(d);
        this.note.reset();
        this.busy.set(false);
      },
      error: (e: unknown) => {
        this.error.set(ApiError.from(e).message);
        this.busy.set(false);
      },
    });
  }
}
