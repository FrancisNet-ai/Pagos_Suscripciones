import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { ApiError } from '../../core/services/api-error';
import { SolesPipe } from '../../shared/pipes/soles.pipe';
import { formatDateTime } from '../orders/orders.api';
import { OrderSummary } from '../orders/orders.api';
import { SupportApi, SupportCustomer } from './support.api';

/** Consulta de soporte en el detalle de un negocio: solo con autorización vigente del titular y motivo. */
@Component({
  selector: 'app-admin-support-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, SolesPipe],
  template: `
    <section class="mt-14 max-w-4xl" aria-labelledby="soporte-titulo">
      <h2 id="soporte-titulo" class="type-h2">Soporte al negocio</h2>
      @if (!until()) {
        <p class="mt-3 text-sm">
          Los pedidos y clientes del negocio no son visibles. Si el titular necesita ayuda, debe autorizar el acceso desde
          su panel (Configuración → Soporte).
        </p>
      } @else {
        <p class="notice notice-info mt-4" role="note">
          El titular autorizó consultas de solo lectura hasta el {{ date(until()!) }}. Cada consulta queda registrada con su motivo y el titular la ve.
        </p>
        <div class="mt-5 grid gap-4 sm:grid-cols-[1fr_14rem]">
          <div class="field">
            <label class="field-label" for="supportReason">Motivo de la consulta</label>
            <input id="supportReason" class="input" [formControl]="reason" maxlength="300" placeholder="Ej.: el titular reporta un pedido duplicado" />
          </div>
          <div class="field">
            <label class="field-label" for="supportQuery">Buscar (opcional)</label>
            <input id="supportQuery" class="input" [formControl]="query" maxlength="80" />
          </div>
        </div>
        <div class="mt-4 flex flex-wrap gap-3">
          <button type="button" class="btn btn-secondary" [disabled]="busy() || reason.invalid" (click)="load('orders')">Ver pedidos</button>
          <button type="button" class="btn btn-secondary" [disabled]="busy() || reason.invalid" (click)="load('customers')">Ver clientes</button>
        </div>
        @if (reason.touched && reason.invalid) { <p class="field-error mt-2">Explica el motivo (entre 10 y 300 caracteres).</p> }
        @if (error(); as e) { <p class="notice notice-error mt-4" role="alert">{{ e }}</p> }

        @if (orders(); as list) {
          <ul class="mt-6 divide-y divide-[var(--line-subtle)] border-y border-[var(--line-subtle)] text-sm" aria-label="Pedidos del negocio">
            @for (o of list; track o.id) {
              <li class="grid gap-1 py-3 sm:grid-cols-[10rem_1fr_7rem_10rem]">
                <span class="type-numeric font-semibold text-night">{{ o.orderNumber }}</span>
                <span>{{ o.customerName }} · {{ o.productName }}</span>
                <span class="type-numeric">{{ o.amount | soles }}</span>
                <span>{{ o.statusLabel }}</span>
              </li>
            } @empty {
              <li class="py-3 text-[var(--text-muted)]">Sin resultados.</li>
            }
          </ul>
        }
        @if (customers(); as list) {
          <ul class="mt-6 divide-y divide-[var(--line-subtle)] border-y border-[var(--line-subtle)] text-sm" aria-label="Clientes del negocio">
            @for (c of list; track c.id) {
              <li class="grid gap-1 py-3 sm:grid-cols-[1fr_10rem_10rem]">
                <span class="font-semibold text-night">{{ c.fullName }}</span>
                <span class="type-numeric">{{ c.phone }}</span>
                <span>{{ c.activeSubscriptions }} suscripciones activas</span>
              </li>
            } @empty {
              <li class="py-3 text-[var(--text-muted)]">Sin resultados.</li>
            }
          </ul>
        }
      }
    </section>
  `,
})
export class AdminSupportPanelComponent {
  readonly businessId = input.required<string>();
  readonly until = input<string | null>(null);

  private readonly api = inject(SupportApi);
  protected readonly reason = new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(10), Validators.maxLength(300)] });
  protected readonly query = new FormControl('', { nonNullable: true });
  protected readonly orders = signal<OrderSummary[] | null>(null);
  protected readonly customers = signal<SupportCustomer[] | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly date = formatDateTime;

  protected load(kind: 'orders' | 'customers'): void {
    if (this.reason.invalid) {
      this.reason.markAsTouched();
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    const reason = this.reason.value.trim();
    const query = this.query.value.trim();
    const done = () => this.busy.set(false);
    const fail = (e: unknown) => {
      this.error.set(ApiError.from(e).message);
      done();
    };
    if (kind === 'orders') {
      this.api.orders(this.businessId(), reason, query).subscribe({
        next: (p) => {
          this.customers.set(null);
          this.orders.set(p.items);
          done();
        },
        error: fail,
      });
    } else {
      this.api.customers(this.businessId(), reason, query).subscribe({
        next: (p) => {
          this.orders.set(null);
          this.customers.set(p.items);
          done();
        },
        error: fail,
      });
    }
  }
}
