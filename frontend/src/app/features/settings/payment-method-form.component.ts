import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Observable } from 'rxjs';
import { ApiError } from '../../core/services/api-error';
import { ImageUploadComponent } from '../../shared/components/image-upload.component';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { applyServerErrors, controlError } from '../../shared/validators/form-errors';
import { peruMobileValidator } from '../../shared/validators/peru-phone';
import { PaymentMethod, SettingsApi } from './settings.api';

@Component({
  selector: 'app-payment-method-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, ImageUploadComponent, StatusBadgeComponent],
  template: `
    @let m = method();
    <section class="grid gap-6 border-t border-[var(--line-subtle)] pt-8" [attr.aria-labelledby]="m.method + '-titulo'">
      <div class="flex flex-wrap items-baseline justify-between gap-3">
        <h2 [id]="m.method + '-titulo'" class="font-[family-name:var(--font-editorial)] text-2xl font-semibold">{{ m.label }}</h2>
        @if (!m.configured) {
          <app-status-badge label="Sin configurar" />
        } @else if (m.enabled) {
          <app-status-badge label="Habilitado para compradores" tone="success" />
        } @else {
          <app-status-badge label="Deshabilitado" tone="warning" />
        }
      </div>

      <form class="grid gap-5" [formGroup]="form" (ngSubmit)="save()" novalidate>
        @if (error(); as message) { <p class="notice notice-error" role="alert">{{ message }}</p> }

        <div class="grid gap-5 sm:grid-cols-2">
          <div class="field">
            <label class="field-label" [for]="m.method + '-holder'">Titular declarado</label>
            <input [id]="m.method + '-holder'" class="input" formControlName="holderName" autocomplete="off"
                   [attr.aria-invalid]="!!err('holderName')" [attr.aria-describedby]="m.method + '-holder-hint'" />
            <p [id]="m.method + '-holder-hint'" class="field-hint">Tal como aparece en {{ m.label }} al pagar.</p>
            @if (err('holderName'); as e) { <p class="field-error">{{ e }}</p> }
          </div>
          <div class="field">
            <label class="field-label" [for]="m.method + '-phone'">Celular que recibe los pagos</label>
            <input [id]="m.method + '-phone'" class="input" type="tel" inputmode="tel" formControlName="phone" placeholder="987 654 321"
                   [attr.aria-invalid]="!!err('phone')" />
            @if (err('phone'); as e) { <p class="field-error">{{ e }}</p> }
          </div>
        </div>

        <label class="flex items-start gap-3">
          <input class="checkbox" type="checkbox" formControlName="enabled" />
          <span>Mostrar {{ m.label }} como método de pago en mi tienda</span>
        </label>

        <div class="flex flex-wrap items-center gap-4">
          <button class="btn btn-primary" type="submit" [disabled]="saving() || (form.pristine && m.configured)">
            @if (saving()) { <span class="spinner" aria-hidden="true"></span> Guardando… } @else { Guardar {{ m.label }} }
          </button>
          <span class="text-sm text-success" role="status" aria-live="polite">{{ saved() ? 'Guardado' : '' }}</span>
        </div>
      </form>

      <div>
        <h3 class="field-label mb-3">Código QR (opcional)</h3>
        <app-image-upload label="QR" noun="código QR" [imageUrl]="m.qrImageUrl" [busy]="uploading()" [disabled]="!m.configured" [error]="qrError()"
                          (selected)="uploadQr($event)" (remove)="removeQr()" />
        @if (!m.configured) { <p class="field-hint mt-2">Guarda primero el titular y el celular para subir el QR.</p> }
      </div>
    </section>
  `,
})
export class PaymentMethodFormComponent implements OnInit {
  readonly initial = input.required<PaymentMethod>();

  private readonly api = inject(SettingsApi);
  protected readonly method = signal<PaymentMethod>({} as PaymentMethod);
  protected readonly saving = signal(false);
  protected readonly saved = signal(false);
  protected readonly uploading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly qrError = signal<string | null>(null);

  protected readonly form = inject(NonNullableFormBuilder).group({
    holderName: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(120)]],
    phone: ['', [Validators.required, peruMobileValidator]],
    enabled: [true],
  });

  ngOnInit(): void {
    this.apply(this.initial());
    this.form.valueChanges.subscribe(() => this.saved.set(false));
  }

  protected err(name: 'holderName' | 'phone'): string | null {
    return controlError(this.form.controls[name]);
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.api.updatePaymentMethod(this.method().method, this.form.getRawValue()).subscribe({
      next: (m) => {
        this.apply(m);
        this.saving.set(false);
        this.saved.set(true);
      },
      error: (e: unknown) => {
        const apiError = ApiError.from(e);
        applyServerErrors(this.form, apiError);
        this.error.set(apiError.message);
        this.saving.set(false);
      },
    });
  }

  protected uploadQr(file: File): void {
    this.runQr(this.api.uploadQr(this.method().method, file));
  }

  protected removeQr(): void {
    this.runQr(this.api.deleteQr(this.method().method));
  }

  private runQr(request: Observable<PaymentMethod>): void {
    this.uploading.set(true);
    this.qrError.set(null);
    request.subscribe({
      next: (m) => {
        this.method.set(m);
        this.uploading.set(false);
      },
      error: (e: unknown) => {
        this.qrError.set(ApiError.from(e).message);
        this.uploading.set(false);
      },
    });
  }

  private apply(m: PaymentMethod): void {
    this.method.set(m);
    this.form.reset(
      { holderName: m.holderName ?? '', phone: m.phone?.replace(/^\+51/, '') ?? '', enabled: m.configured ? m.enabled : true },
      { emitEvent: false },
    );
  }
}
