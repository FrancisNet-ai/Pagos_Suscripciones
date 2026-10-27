import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiError } from '../../core/services/api-error';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { SolesPipe } from '../../shared/pipes/soles.pipe';
import { formatDateTime, orderTone } from '../orders/orders.api';
import { ManualSubscriptionFieldsComponent, manualSubscriptionForm, toManualPayload } from '../subscriptions/manual-subscription-fields.component';
import { SubscriptionItemComponent } from '../subscriptions/subscription-item.component';
import { Subscription } from '../subscriptions/subscriptions.api';
import { CustomerDetail, CustomersApi } from './customers.api';

@Component({
  selector: 'app-customer-detail-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, StatusBadgeComponent, SolesPipe, SubscriptionItemComponent, ManualSubscriptionFieldsComponent],
  templateUrl: './customer-detail.page.html',
})
export class CustomerDetailPage implements OnInit {
  readonly id = input.required<string>();

  private readonly api = inject(CustomersApi);
  private readonly fb = inject(NonNullableFormBuilder);
  protected readonly date = formatDateTime;
  protected readonly tone = orderTone;

  protected readonly customer = signal<CustomerDetail | null>(null);
  protected readonly loadError = signal<string | null>(null);
  protected readonly editing = signal(false);
  protected readonly adding = signal(false);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly editForm = this.fb.group({
    fullName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(120)]],
    notes: ['', [Validators.maxLength(1000)]],
  });
  protected readonly subscriptionForm = manualSubscriptionForm(this.fb);

  ngOnInit(): void {
    this.api.get(this.id()).subscribe({
      next: (c) => this.customer.set(c),
      error: (e: unknown) => this.loadError.set(ApiError.from(e).message),
    });
  }

  protected startEdit(c: CustomerDetail): void {
    this.editForm.reset({ fullName: c.fullName, notes: c.notes ?? '' });
    this.editing.set(true);
  }

  protected saveEdit(): void {
    if (this.editForm.invalid) {
      this.editForm.markAllAsTouched();
      return;
    }
    const v = this.editForm.getRawValue();
    this.run(this.api.update(this.id(), { fullName: v.fullName.trim(), notes: v.notes.trim() || null }), () => this.editing.set(false));
  }

  protected saveSubscription(): void {
    if (this.subscriptionForm.invalid) {
      this.subscriptionForm.markAllAsTouched();
      return;
    }
    this.run(this.api.addSubscription(this.id(), toManualPayload(this.subscriptionForm)), () => {
      this.adding.set(false);
      this.subscriptionForm.reset();
    });
  }

  protected onSubscriptionChanged(updated: Subscription): void {
    this.customer.update((c) => (c ? { ...c, subscriptions: c.subscriptions.map((s) => (s.id === updated.id ? updated : s)) } : c));
  }

  protected local(phone: string): string {
    const l = phone.replace(/^\+51/, '');
    return `${l.slice(0, 3)} ${l.slice(3, 6)} ${l.slice(6)}`;
  }

  protected whatsapp(c: CustomerDetail): string {
    return `https://wa.me/${c.phone.replace('+', '')}`;
  }

  private run(request: ReturnType<CustomersApi['get']>, after: () => void): void {
    this.busy.set(true);
    this.error.set(null);
    request.subscribe({
      next: (c) => {
        this.customer.set(c);
        this.busy.set(false);
        after();
      },
      error: (e: unknown) => {
        const apiError = ApiError.from(e);
        for (const fe of apiError.fieldErrors) {
          const control = this.subscriptionForm.get(fe.field);
          control?.setErrors({ server: fe.message });
          control?.markAsTouched();
        }
        this.error.set(apiError.message);
        this.busy.set(false);
      },
    });
  }
}
