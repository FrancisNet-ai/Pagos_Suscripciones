import { Clipboard } from '@angular/cdk/clipboard';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiError } from '../../core/services/api-error';
import { storeTheme } from '../../shared/components/store-theme';
import { DurationPipe, SolesPipe } from '../../shared/pipes/soles.pipe';
import { applyServerErrors, controlError } from '../../shared/validators/form-errors';
import { PublicOrder, StorefrontApi } from '../storefront/storefront.api';

const MAX_RECEIPT = 5 * 1024 * 1024;

@Component({
  selector: 'app-order-status-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, SolesPipe, DurationPipe],
  templateUrl: './order-status.page.html',
})
export class OrderStatusPage implements OnInit {
  readonly token = input.required<string>();

  private readonly api = inject(StorefrontApi);
  private readonly clipboard = inject(Clipboard);

  protected readonly order = signal<PublicOrder | null>(null);
  protected readonly loadError = signal<string | null>(null);
  protected readonly refreshing = signal(false);
  protected readonly reporting = signal(false);
  protected readonly reportError = signal<string | null>(null);
  protected readonly receipt = signal<File | null>(null);
  protected readonly receiptError = signal<string | null>(null);
  protected readonly copied = signal<'phone' | 'link' | null>(null);
  protected readonly theme = computed(() => storeTheme(this.order()?.store.primaryColor));

  protected readonly form = inject(NonNullableFormBuilder).group({
    transactionReference: ['', [Validators.required, Validators.pattern(/^[A-Za-z0-9-]{4,40}$/)]],
  });

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.refreshing.set(true);
    this.api.order(this.token()).subscribe({
      next: (o) => {
        this.order.set(o);
        this.refreshing.set(false);
      },
      error: (e: unknown) => {
        this.loadError.set(ApiError.from(e).message);
        this.refreshing.set(false);
      },
    });
  }

  protected referenceError(): string | null {
    const control = this.form.controls.transactionReference;
    if (control.touched && control.errors?.['pattern']) return 'Usa solo letras y números (4 a 40 caracteres)';
    return controlError(control);
  }

  protected onReceipt(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0] ?? null;
    this.receiptError.set(null);
    if (file && !['image/png', 'image/jpeg', 'image/webp'].includes(file.type)) {
      this.receiptError.set('Adjunta una captura en PNG, JPG o WebP.');
      return;
    }
    if (file && file.size > MAX_RECEIPT) {
      this.receiptError.set('La imagen supera los 5 MB.');
      return;
    }
    this.receipt.set(file);
  }

  protected report(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.reporting.set(true);
    this.reportError.set(null);
    this.api.reportPayment(this.token(), this.form.controls.transactionReference.value.trim(), this.receipt()).subscribe({
      next: (o) => {
        this.order.set(o);
        this.reporting.set(false);
        this.form.reset();
        this.receipt.set(null);
      },
      error: (e: unknown) => {
        const apiError = ApiError.from(e);
        applyServerErrors(this.form, apiError);
        this.reportError.set(apiError.message);
        this.reporting.set(false);
      },
    });
  }

  protected copy(kind: 'phone' | 'link', value: string): void {
    if (this.clipboard.copy(value)) {
      this.copied.set(kind);
      setTimeout(() => this.copied.set(null), 2500);
    }
  }

  protected pageUrl(): string {
    return window.location.href;
  }

  protected localPhone(e164: string): string {
    const local = e164.replace(/^\+51/, '');
    return `${local.slice(0, 3)} ${local.slice(3, 6)} ${local.slice(6)}`;
  }

  protected time(iso: string): string {
    return new Intl.DateTimeFormat('es-PE', { hour: '2-digit', minute: '2-digit' }).format(new Date(iso));
  }
}
