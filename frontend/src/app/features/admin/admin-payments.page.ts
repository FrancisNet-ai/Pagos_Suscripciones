import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiError } from '../../core/services/api-error';
import { Page } from '../../core/services/page';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { SolesPipe } from '../../shared/pipes/soles.pipe';
import { SaasPaymentStatus } from '../billing/billing.api';
import { AdminApi, AdminSaasPayment } from './admin.api';

type Tab = SaasPaymentStatus | 'ALL';

@Component({
  selector: 'app-admin-payments-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, SolesPipe, StatusBadgeComponent],
  template: `
    <p class="type-eyebrow">Nuvyra Billing</p>
    <h1 class="type-h1 mt-3">Pagos SaaS</h1>
    <p class="mt-3 max-w-2xl">
      Revisa en la aplicación de Yape o Plin de Nuvyra que el dinero llegó antes de aprobar. Un comprobante nunca activa un plan por sí solo.
    </p>

    <nav class="mt-8 flex gap-1 overflow-x-auto border-b border-[var(--line-subtle)] pb-4" aria-label="Estado del pago">
      @for (t of tabs; track t.value) {
        <button type="button" class="whitespace-nowrap rounded-full px-4 py-2 text-sm font-semibold"
                [class]="tab() === t.value ? 'bg-night text-snow' : 'text-corporate hover:bg-mist/30'" [attr.aria-pressed]="tab() === t.value" (click)="select(t.value)">{{ t.label }}</button>
      }
    </nav>

    @if (notice(); as n) { <p class="notice notice-success mt-6" role="status">{{ n }}</p> }
    @if (error(); as e) { <p class="notice notice-error mt-6" role="alert">{{ e }}</p> }

    @if (page(); as p) {
      @if (p.items.length === 0) {
        <p class="py-16 text-center font-[family-name:var(--font-editorial)] text-2xl text-night">{{ tab() === 'PENDING_VERIFICATION' ? 'No hay pagos por verificar.' : 'No hay pagos en este grupo.' }}</p>
      } @else {
        <ul class="divide-y divide-[var(--line-subtle)]">
          @for (pay of p.items; track pay.id) {
            <li class="grid gap-4 py-6 lg:grid-cols-[1.3fr_1fr] lg:gap-10">
              <div>
                <div class="flex flex-wrap items-baseline justify-between gap-3">
                  <a [routerLink]="['/admin/negocios', pay.businessId]" class="font-semibold text-night no-underline hover:underline">{{ pay.businessName }}</a>
                  <span class="type-numeric font-[family-name:var(--font-display)] text-2xl text-night">{{ pay.amount | soles }}</span>
                </div>
                <p class="mt-1 text-sm">{{ pay.invoiceNumber }} · {{ pay.planName }}</p>
                <dl class="mt-3 grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-sm">
                  <dt class="text-[var(--text-muted)]">Método</dt><dd>{{ pay.method === 'YAPE' ? 'Yape' : 'Plin' }} a {{ pay.receiverHolder }} ({{ local(pay.receiverPhone) }})</dd>
                  <dt class="text-[var(--text-muted)]">Código</dt><dd class="type-numeric font-semibold text-night">{{ pay.transactionReference }}</dd>
                  <dt class="text-[var(--text-muted)]">Reportado</dt><dd>{{ dateTime(pay.reportedAt) }}</dd>
                  @if (pay.verifiedAt) { <dt class="text-[var(--text-muted)]">Verificado</dt><dd>{{ dateTime(pay.verifiedAt) }}</dd> }
                  @if (pay.rejectionReason) { <dt class="text-[var(--text-muted)]">Motivo</dt><dd>{{ pay.rejectionReason }}</dd> }
                </dl>
                <div class="mt-3"><app-status-badge [label]="label(pay)" [tone]="tone(pay)" /></div>
              </div>

              <div class="grid content-start gap-3">
                @if (pay.hasReceipt) {
                  @if (receipts()[pay.id]; as url) {
                    <img [src]="url" alt="Comprobante reportado por el vendedor" class="max-h-80 w-full rounded object-contain ring-1 ring-[var(--line-subtle)]" />
                  } @else {
                    <button type="button" class="btn btn-secondary !min-h-10 justify-self-start" (click)="showReceipt(pay.id)">Ver comprobante</button>
                  }
                } @else {
                  <p class="text-sm text-[var(--text-muted)]">Sin captura adjunta.</p>
                }
                @if (pay.status === 'PENDING_VERIFICATION') {
                  <label class="flex gap-3 text-sm">
                    <input type="checkbox" class="checkbox" [checked]="confirmed().has(pay.id)" (change)="toggleConfirm(pay.id)" />
                    <span>Comprobé en la aplicación que recibí {{ pay.amount | soles }} con el código {{ pay.transactionReference }}.</span>
                  </label>
                  <div class="flex flex-wrap gap-2">
                    <button type="button" class="btn btn-primary !min-h-10" [disabled]="busy() || !confirmed().has(pay.id)" (click)="verify(pay)">Aprobar y activar plan</button>
                    <button type="button" class="btn btn-secondary !min-h-10" [disabled]="busy()" (click)="rejecting.set(rejecting() === pay.id ? null : pay.id)">Rechazar</button>
                  </div>
                  @if (rejecting() === pay.id) {
                    <div class="field">
                      <label class="field-label" [attr.for]="'reason-' + pay.id">Motivo (lo verá el vendedor)</label>
                      <input class="input" [id]="'reason-' + pay.id" #reason maxlength="300" placeholder="No encontramos el pago con ese código" />
                      <button type="button" class="btn btn-secondary !min-h-10 mt-2 justify-self-start" [disabled]="busy()" (click)="reject(pay, reason.value)">Confirmar rechazo</button>
                    </div>
                  }
                }
              </div>
            </li>
          }
        </ul>
        @if (p.totalPages > 1) {
          <div class="mt-6 flex items-center justify-between text-sm">
            <button type="button" class="btn btn-secondary !min-h-10" [disabled]="p.page === 0" (click)="load(p.page - 1)">Anterior</button>
            <span>Página {{ p.page + 1 }} de {{ p.totalPages }}</span>
            <button type="button" class="btn btn-secondary !min-h-10" [disabled]="p.page + 1 >= p.totalPages" (click)="load(p.page + 1)">Siguiente</button>
          </div>
        }
      }
    } @else if (!error()) {
      <div class="mt-6 h-40 animate-pulse rounded bg-mist/25" aria-busy="true"></div>
    }
  `,
})
export class AdminPaymentsPage {
  private readonly api = inject(AdminApi);
  protected readonly tabs: { value: Tab; label: string }[] = [
    { value: 'PENDING_VERIFICATION', label: 'Por verificar' },
    { value: 'VERIFIED', label: 'Verificados' },
    { value: 'REJECTED', label: 'Rechazados' },
    { value: 'ALL', label: 'Todos' },
  ];
  protected readonly tab = signal<Tab>('PENDING_VERIFICATION');
  protected readonly page = signal<Page<AdminSaasPayment> | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly confirmed = signal(new Set<string>());
  protected readonly rejecting = signal<string | null>(null);
  protected readonly receipts = signal<Record<string, string>>({});

  constructor() {
    this.load(0);
    inject(DestroyRef).onDestroy(() => Object.values(this.receipts()).forEach((u) => URL.revokeObjectURL(u)));
  }

  protected select(tab: Tab): void {
    this.tab.set(tab);
    this.notice.set(null);
    this.load(0);
  }

  protected load(page: number): void {
    this.error.set(null);
    const tab = this.tab();
    this.api.payments(tab === 'ALL' ? null : tab, page).subscribe({
      next: (p) => this.page.set(p),
      error: (e: unknown) => this.error.set(ApiError.from(e).message),
    });
  }

  protected toggleConfirm(id: string): void {
    const next = new Set(this.confirmed());
    if (next.has(id)) next.delete(id);
    else next.add(id);
    this.confirmed.set(next);
  }

  protected verify(pay: AdminSaasPayment): void {
    this.act(this.api.verify(pay.id), `Pago aprobado. El plan de ${pay.businessName} quedó activo.`);
  }

  protected reject(pay: AdminSaasPayment, reason: string): void {
    if (reason.trim().length < 5) {
      this.error.set('Indica un motivo de al menos 5 caracteres.');
      return;
    }
    this.act(this.api.reject(pay.id, reason.trim()), 'Pago rechazado. El vendedor verá el motivo y podrá reportarlo de nuevo.');
  }

  protected showReceipt(id: string): void {
    this.api.receipt(id).subscribe({
      next: (url) => this.receipts.set({ ...this.receipts(), [id]: url }),
      error: (e: unknown) => this.error.set(ApiError.from(e).message),
    });
  }

  protected label(p: AdminSaasPayment): string {
    return { PENDING_VERIFICATION: 'Por verificar', VERIFIED: 'Verificado', REJECTED: 'Rechazado' }[p.status];
  }

  protected tone(p: AdminSaasPayment): 'warning' | 'success' | 'danger' {
    return ({ PENDING_VERIFICATION: 'warning', VERIFIED: 'success', REJECTED: 'danger' } as const)[p.status];
  }

  protected local(e164: string): string {
    const n = e164.replace(/^\+51/, '');
    return `${n.slice(0, 3)} ${n.slice(3, 6)} ${n.slice(6)}`;
  }

  protected dateTime(iso: string): string {
    return new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(iso));
  }

  private act(request: ReturnType<AdminApi['verify']>, success: string): void {
    this.busy.set(true);
    this.error.set(null);
    request.subscribe({
      next: () => {
        this.notice.set(success);
        this.rejecting.set(null);
        this.busy.set(false);
        this.load(this.page()?.page ?? 0);
      },
      error: (e: unknown) => {
        this.error.set(ApiError.from(e).message);
        this.busy.set(false);
      },
    });
  }
}
