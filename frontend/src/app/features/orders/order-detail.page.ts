import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { Observable } from 'rxjs';
import { ApiError } from '../../core/services/api-error';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { DurationPipe, SolesPipe } from '../../shared/pipes/soles.pipe';
import { METHOD_LABEL, OrderDetail, OrdersApi, PaymentDetail, formatDateTime, orderTone } from './orders.api';

type Panel = 'none' | 'reject' | 'cancel' | 'refund';

const PAYMENT_LABEL: Record<string, string> = {
  PENDING: 'Sin reportar',
  PENDING_VERIFICATION: 'Por verificar',
  VERIFIED: 'Verificado',
  REJECTED: 'Rechazado',
  CANCELLED: 'Cancelado',
  REPORTED: 'Reportado',
  REFUNDED: 'Reembolsado',
};

@Component({
  selector: 'app-order-detail-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, StatusBadgeComponent, SolesPipe, DurationPipe],
  templateUrl: './order-detail.page.html',
})
export class OrderDetailPage implements OnInit {
  readonly id = input.required<string>();

  private readonly api = inject(OrdersApi);
  private readonly auth = inject(AuthService);
  protected readonly methodLabel = METHOD_LABEL;
  protected readonly paymentLabel = PAYMENT_LABEL;
  protected readonly tone = orderTone;
  protected readonly date = formatDateTime;

  protected readonly order = signal<OrderDetail | null>(null);
  protected readonly loadError = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly actionError = signal<string | null>(null);
  protected readonly panel = signal<Panel>('none');
  protected readonly receiptUrl = signal<string | null>(null);
  protected readonly receiptError = signal<string | null>(null);
  protected readonly confirmReceived = new FormControl(false, { nonNullable: true });
  protected readonly reason = new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(5), Validators.maxLength(300)] });

  /** Pago vigente: el más reciente. */
  protected readonly current = computed<PaymentDetail | null>(() => this.order()?.payments[0] ?? null);
  protected readonly canVerify = computed(() => this.current()?.status === 'PENDING_VERIFICATION');
  /** Solo el titular registra reembolsos de pagos verificados. */
  protected readonly canRefund = computed(() => this.auth.isOwner() && this.current()?.status === 'VERIFIED');
  protected readonly confirmReturned = new FormControl(false, { nonNullable: true });
  protected readonly refundReference = new FormControl('', { nonNullable: true, validators: [Validators.maxLength(40)] });
  protected readonly canCancel = computed(() => ['AWAITING_PAYMENT', 'PAYMENT_REPORTED'].includes(this.order()?.status ?? ''));

  constructor() {
    inject(DestroyRef).onDestroy(() => this.revokeReceipt());
  }

  ngOnInit(): void {
    this.api.get(this.id()).subscribe({
      next: (o) => this.order.set(o),
      error: (e: unknown) => this.loadError.set(ApiError.from(e).message),
    });
  }

  protected openPanel(panel: Panel): void {
    this.panel.set(panel);
    this.reason.reset();
    this.confirmReturned.reset();
    this.refundReference.reset();
    this.actionError.set(null);
  }

  protected verify(): void {
    const payment = this.current();
    if (!payment || !this.confirmReceived.value) return;
    this.run(this.api.verify(payment.id));
  }

  protected submitReason(): void {
    if (this.reason.invalid) {
      this.reason.markAsTouched();
      return;
    }
    const value = this.reason.value.trim();
    if (this.panel() === 'refund') {
      if (!this.confirmReturned.value) return;
      this.run(this.api.refund(this.current()!.id, value, this.refundReference.value.trim() || null));
      return;
    }
    const request = this.panel() === 'reject' ? this.api.reject(this.current()!.id, value) : this.api.cancel(this.order()!.id, value);
    this.run(request);
  }

  protected showReceipt(paymentId: string): void {
    this.receiptError.set(null);
    this.api.receipt(paymentId).subscribe({
      next: (url) => {
        this.revokeReceipt();
        this.receiptUrl.set(url);
      },
      error: (e: unknown) => this.receiptError.set(ApiError.from(e).message),
    });
  }

  protected reasonError(): string | null {
    const c = this.reason;
    if (!c.touched || !c.errors) return null;
    return c.errors['required'] ? 'Indica el motivo' : 'El motivo debe tener entre 5 y 300 caracteres';
  }

  protected localPhone(e164: string): string {
    const local = e164.replace(/^\+51/, '');
    return `${local.slice(0, 3)} ${local.slice(3, 6)} ${local.slice(6)}`;
  }

  private run(request: Observable<OrderDetail>): void {
    this.busy.set(true);
    this.actionError.set(null);
    request.subscribe({
      next: (o) => {
        this.order.set(o);
        this.busy.set(false);
        this.panel.set('none');
        this.confirmReceived.reset();
      },
      error: (e: unknown) => {
        this.actionError.set(ApiError.from(e).message);
        this.busy.set(false);
        // Otro dispositivo pudo haber procesado el pago: recargar el estado real.
        this.api.get(this.id()).subscribe((o) => this.order.set(o));
      },
    });
  }

  private revokeReceipt(): void {
    const url = this.receiptUrl();
    if (url) URL.revokeObjectURL(url);
  }
}
