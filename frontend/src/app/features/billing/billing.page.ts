import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../core/auth/auth.service';
import { ApiError } from '../../core/services/api-error';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { SolesPipe } from '../../shared/pipes/soles.pipe';
import { BillingApi, BillingOverview, Invoice, Method, Plan, formatDate } from './billing.api';
import { PlanCardComponent } from './plan-card.component';

const MAX_RECEIPT_BYTES = 5 * 1024 * 1024;

@Component({
  selector: 'app-billing-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, PlanCardComponent, SolesPipe, StatusBadgeComponent],
  templateUrl: './billing.page.html',
})
export class BillingPage {
  private readonly api = inject(BillingApi);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly data = signal<BillingOverview | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly receipt = signal<File | null>(null);
  protected readonly receiptError = signal<string | null>(null);
  protected readonly formatDate = formatDate;

  protected readonly form = this.fb.group({
    method: this.fb.control<Method | ''>('', Validators.required),
    transactionReference: ['', [Validators.required, Validators.pattern(/^[A-Za-z0-9-]{4,40}$/)]],
  });

  protected readonly open = computed(() => this.data()?.openInvoice ?? null);
  protected readonly pendingPayment = computed(() => this.open()?.payments.find((p) => p.status === 'PENDING_VERIFICATION') ?? null);
  protected readonly lastRejected = computed(() => {
    const latest = this.open()?.payments[0];
    return latest?.status === 'REJECTED' ? latest : null;
  });
  /** Planes a elegir: los activos y, si ya no se ofrece, el plan actual para poder renovarlo. */
  protected readonly plans = computed<Plan[]>(() => {
    const d = this.data();
    if (!d) return [];
    const current = d.currentPlan;
    return current && !d.plans.some((p) => p.id === current.id) ? [current, ...d.plans] : d.plans;
  });
  protected readonly hasPeriod = computed(() => {
    const s = this.data()?.access.subscriptionStatus;
    return s === 'ACTIVE' || s === 'PAST_DUE' || s === 'EXPIRED';
  });
  protected readonly selectedMethod = computed(() => this.data()?.paymentOptions.find((o) => o.method === this.methodValue()) ?? null);
  private readonly methodValue = signal<Method | ''>('');

  constructor() {
    this.form.controls.method.valueChanges.subscribe((m) => this.methodValue.set(m));
    this.load();
  }

  protected load(): void {
    this.api.overview().subscribe({
      next: (d) => this.apply(d),
      error: (e: unknown) => this.error.set(ApiError.from(e).message),
    });
  }

  protected actionLabel(plan: Plan): string {
    const d = this.data();
    if (this.open()?.planId === plan.id) return 'Cobro generado';
    if (d?.currentPlan?.id === plan.id && this.hasPeriod()) return 'Renovar este plan';
    return plan.price === 0 ? 'Activar gratis' : 'Elegir este plan';
  }

  protected isCurrent(plan: Plan): boolean {
    return this.data()?.currentPlan?.id === plan.id && this.hasPeriod();
  }

  protected choose(plan: Plan): void {
    this.run(this.api.checkout(plan.id), plan.price === 0 ? 'Tu plan está activo.' : 'Generamos tu cobro. Paga con Yape o Plin y reporta el código de operación.');
  }

  protected cancel(invoice: Invoice): void {
    this.run(this.api.cancel(invoice.id), 'Anulamos el cobro.');
  }

  protected onReceipt(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0] ?? null;
    this.receiptError.set(null);
    if (file && !['image/png', 'image/jpeg', 'image/webp'].includes(file.type)) {
      this.receiptError.set('Adjunta una captura en PNG, JPG o WebP.');
      return;
    }
    if (file && file.size > MAX_RECEIPT_BYTES) {
      this.receiptError.set('La imagen supera los 5 MB.');
      return;
    }
    this.receipt.set(file);
  }

  protected report(invoice: Invoice): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { method, transactionReference } = this.form.getRawValue();
    this.run(
      this.api.reportPayment(invoice.id, method as Method, transactionReference.trim(), this.receipt()),
      'Recibimos tu reporte. Activaremos tu plan cuando confirmemos el pago.',
      () => {
        this.form.reset({ method: '', transactionReference: '' });
        this.receipt.set(null);
      },
    );
  }

  protected referenceError(): string | null {
    const c = this.form.controls.transactionReference;
    if (!c.touched || !c.errors) return null;
    return c.errors['required'] ? 'Ingresa el código de operación' : 'Usa entre 4 y 40 letras o números';
  }

  protected usagePercent(used: number, limit: number | null): number {
    return limit ? Math.min(100, Math.round((used / limit) * 100)) : 0;
  }

  protected localPhone(e164: string): string {
    const local = e164.replace(/^\+51/, '');
    return `${local.slice(0, 3)} ${local.slice(3, 6)} ${local.slice(6)}`;
  }

  protected invoiceTone(i: Invoice): 'success' | 'neutral' | 'warning' {
    return i.status === 'PAID' ? 'success' : i.status === 'VOID' ? 'neutral' : 'warning';
  }

  protected invoiceLabel(i: Invoice): string {
    return { PAID: 'Pagado', VOID: 'Anulado', OPEN: 'Pendiente' }[i.status];
  }

  private run(request: ReturnType<BillingApi['overview']>, success: string, after?: () => void): void {
    this.busy.set(true);
    this.error.set(null);
    this.notice.set(null);
    request.subscribe({
      next: (d) => {
        this.apply(d);
        this.notice.set(success);
        this.busy.set(false);
        after?.();
        // El aviso del panel y los permisos dependen de la sesión: se refresca tras cada cambio.
        this.auth.loadSession().subscribe();
      },
      error: (e: unknown) => {
        this.error.set(ApiError.from(e).message);
        this.busy.set(false);
      },
    });
  }

  private apply(d: BillingOverview): void {
    this.data.set(d);
    if (d.paymentOptions.length === 1 && !this.form.controls.method.value) this.form.controls.method.setValue(d.paymentOptions[0]!.method);
  }
}
