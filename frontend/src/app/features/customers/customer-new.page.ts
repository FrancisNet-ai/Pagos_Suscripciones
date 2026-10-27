import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiError } from '../../core/services/api-error';
import { applyServerErrors, controlError } from '../../shared/validators/form-errors';
import { peruMobileValidator } from '../../shared/validators/peru-phone';
import { ManualSubscriptionFieldsComponent, manualSubscriptionForm, toManualPayload } from '../subscriptions/manual-subscription-fields.component';
import { CustomersApi } from './customers.api';

@Component({
  selector: 'app-customer-new-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, ManualSubscriptionFieldsComponent],
  template: `
    <section class="mx-auto max-w-3xl">
      <a routerLink="/panel/clientes" class="link text-sm">← Volver a clientes</a>
      <p class="type-eyebrow mt-4">Registrar cliente</p>
      <h1 class="type-h1 mt-3">Un cliente que ya tenías</h1>
      <p class="mt-3 max-w-2xl">Registra a quienes te compraron antes de usar la plataforma para controlar sus vencimientos. No se crea ningún pedido ni se registra como venta.</p>

      @if (error(); as message) {
        <p class="notice notice-error mt-6" role="alert">
          {{ message }}
          @if (existingId(); as id) { &nbsp;<a class="link" [routerLink]="['/panel/clientes', id]">Ver cliente</a> }
        </p>
      }

      <form class="mt-8 grid gap-8" [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <div class="grid gap-5 sm:grid-cols-2">
          <div class="field">
            <label class="field-label" for="fullName">Nombre</label>
            <input id="fullName" class="input" formControlName="fullName" [attr.aria-invalid]="!!err('fullName')" />
            @if (err('fullName'); as e) { <p class="field-error">{{ e }}</p> }
          </div>
          <div class="field">
            <label class="field-label" for="phone">Celular</label>
            <input id="phone" class="input" type="tel" inputmode="tel" formControlName="phone" placeholder="987 654 321" [attr.aria-invalid]="!!err('phone')" />
            @if (err('phone'); as e) { <p class="field-error">{{ e }}</p> }
          </div>
        </div>
        <div class="field">
          <label class="field-label" for="notes">Notas internas (opcional)</label>
          <textarea id="notes" class="input min-h-20" formControlName="notes" maxlength="1000"></textarea>
        </div>

        <div class="border-t border-[var(--line-subtle)] pt-6">
          <label class="flex items-start gap-3">
            <input class="checkbox" type="checkbox" [formControl]="withSubscription" />
            <span><span class="font-semibold text-night">Registrar su suscripción actual</span><br /><span class="text-sm">Producto, fecha de inicio y duración para calcular su vencimiento.</span></span>
          </label>
          @if (withSubscription.value) {
            <div class="mt-6"><app-manual-subscription-fields [form]="subscription" /></div>
          }
        </div>

        <div>
          <button class="btn btn-primary" type="submit" [disabled]="saving()">
            @if (saving()) { <span class="spinner" aria-hidden="true"></span> Guardando… } @else { Registrar cliente }
          </button>
        </div>
      </form>
    </section>
  `,
})
export class CustomerNewPage {
  private readonly api = inject(CustomersApi);
  private readonly router = inject(Router);
  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly existingId = signal<string | null>(null);
  protected readonly withSubscription = new FormControl(true, { nonNullable: true });
  protected readonly subscription = manualSubscriptionForm(this.fb);
  protected readonly form = this.fb.group({
    fullName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(120)]],
    phone: ['', [Validators.required, peruMobileValidator]],
    notes: ['', [Validators.maxLength(1000)]],
  });

  protected err(name: 'fullName' | 'phone'): string | null {
    return controlError(this.form.controls[name]);
  }

  protected submit(): void {
    const withSub = this.withSubscription.value;
    if (this.form.invalid || (withSub && this.subscription.invalid)) {
      this.form.markAllAsTouched();
      this.subscription.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.error.set(null);
    this.existingId.set(null);
    this.api
      .create({ fullName: v.fullName.trim(), phone: v.phone, notes: v.notes.trim() || null, subscription: withSub ? toManualPayload(this.subscription) : null })
      .subscribe({
        next: (c) => void this.router.navigate(['/panel/clientes', c.id]),
        error: (e: unknown) => {
          const apiError = ApiError.from(e);
          applyServerErrors(this.form, apiError);
          // Los errores de la suscripción llegan como "subscription.campo".
          for (const fe of apiError.fieldErrors) {
            const control = this.subscription.get(fe.field.replace(/^subscription\./, ''));
            if (fe.field.startsWith('subscription.') || ['status', 'startDate'].includes(fe.field)) {
              control?.setErrors({ server: fe.message });
              control?.markAsTouched();
            }
          }
          const body = (e as { error?: { customerId?: string } }).error;
          this.existingId.set(body?.customerId ?? null);
          this.error.set(apiError.message);
          this.saving.set(false);
        },
      });
  }
}
