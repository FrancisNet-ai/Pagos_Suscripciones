import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ApiError } from '../../core/services/api-error';
import { storeTheme } from '../../shared/components/store-theme';
import { DurationPipe, SolesPipe } from '../../shared/pipes/soles.pipe';
import { applyServerErrors, controlError } from '../../shared/validators/form-errors';
import { peruMobileValidator } from '../../shared/validators/peru-phone';
import { PaymentMethodType, PublicProduct, PublicStore, StorefrontApi } from '../storefront/storefront.api';

@Component({
  selector: 'app-checkout-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, SolesPipe, DurationPipe],
  templateUrl: './checkout.page.html',
})
export class CheckoutPage implements OnInit {
  readonly slug = input.required<string>();
  readonly productId = input.required<string>();

  private readonly api = inject(StorefrontApi);
  private readonly router = inject(Router);
  /** Una clave por intento de compra: reintentar el envío no duplica el pedido. */
  private readonly idempotencyKey = crypto.randomUUID();

  protected readonly store = signal<PublicStore | null>(null);
  protected readonly product = signal<PublicProduct | null>(null);
  protected readonly loadError = signal<string | null>(null);
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly theme = computed(() => storeTheme(this.store()?.primaryColor));

  private readonly fb = inject(NonNullableFormBuilder);
  protected readonly form = this.fb.group({
    customerName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(120)]],
    phone: ['', [Validators.required, peruMobileValidator]],
    paymentMethod: this.fb.control<PaymentMethodType | ''>('', [Validators.required]),
    acceptTerms: [false, [Validators.requiredTrue]],
    acceptDataProcessing: [false, [Validators.requiredTrue]],
    acceptRenewalReminders: [false],
    acceptOrderUpdates: [false],
  });

  ngOnInit(): void {
    forkJoin({ store: this.api.store(this.slug()), product: this.api.product(this.slug(), this.productId()) }).subscribe({
      next: ({ store, product }) => {
        this.store.set(store);
        this.product.set(product);
        if (store.paymentMethods.length === 1) this.form.controls.paymentMethod.setValue(store.paymentMethods[0].method);
      },
      error: (e: unknown) => this.loadError.set(ApiError.from(e).message),
    });
  }

  protected err(name: 'customerName' | 'phone' | 'paymentMethod' | 'acceptTerms' | 'acceptDataProcessing'): string | null {
    const control = this.form.controls[name];
    if (name === 'paymentMethod' && control.touched && control.errors?.['required']) return 'Selecciona cómo pagarás';
    return controlError(control);
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.submitting.set(true);
    this.error.set(null);
    this.api
      .createOrder(
        this.slug(),
        {
          productId: this.productId(),
          customerName: v.customerName.trim(),
          phone: v.phone,
          paymentMethod: v.paymentMethod as PaymentMethodType,
          acceptTerms: v.acceptTerms,
          acceptDataProcessing: v.acceptDataProcessing,
          acceptRenewalReminders: v.acceptRenewalReminders,
          acceptOrderUpdates: offersUpdates(this.store()) && v.acceptOrderUpdates,
        },
        this.idempotencyKey,
      )
      .subscribe({
        // El pedido ya está guardado: la página del pedido muestra cómo pagar.
        next: ({ publicToken }) => void this.router.navigate(['/pedido', publicToken], { replaceUrl: true }),
        error: (e: unknown) => {
          const apiError = ApiError.from(e);
          applyServerErrors(this.form, apiError);
          this.error.set(apiError.message);
          this.submitting.set(false);
        },
      });
  }
}

/** Solo se registra la autorización si la tienda realmente ofrece las confirmaciones. */
function offersUpdates(store: PublicStore | null): boolean {
  return store?.orderUpdatesByWhatsapp ?? false;
}
