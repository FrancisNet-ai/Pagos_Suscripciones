import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Observable } from 'rxjs';
import { ApiError } from '../../core/services/api-error';
import { ImageUploadComponent } from '../../shared/components/image-upload.component';
import { applyServerErrors, controlError } from '../../shared/validators/form-errors';
import { peruMobileValidator } from '../../shared/validators/peru-phone';
import { Method } from '../billing/billing.api';
import { AdminApi, PlatformPaymentMethod, PlatformSettings } from './admin.api';

type MethodForm = FormGroup<{ holderName: FormControl<string>; phone: FormControl<string>; enabled: FormControl<boolean> }>;

@Component({
  selector: 'app-admin-settings-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, ImageUploadComponent],
  template: `
    <p class="type-eyebrow">Plataforma</p>
    <h1 class="type-h1 mt-3">Configuración general</h1>

    @if (notice(); as n) { <p class="notice notice-success mt-6" role="status">{{ n }}</p> }
    @if (error(); as e) { <p class="notice notice-error mt-6" role="alert">{{ e }}</p> }

    @if (settings(); as s) {
      <form class="mt-10 grid max-w-3xl gap-6" [formGroup]="general" (ngSubmit)="saveGeneral()" novalidate>
        <h2 class="type-h2">Soporte y plazo de gracia</h2>
        <div class="grid gap-5 sm:grid-cols-2">
          <div class="field">
            <label class="field-label" for="supportWhatsapp">WhatsApp de soporte</label>
            <input id="supportWhatsapp" class="input" type="tel" formControlName="supportWhatsapp" placeholder="987 654 321" />
            @if (err(general.controls.supportWhatsapp); as e) { <p class="field-error">{{ e }}</p> }
          </div>
          <div class="field">
            <label class="field-label" for="supportEmail">Correo de soporte</label>
            <input id="supportEmail" class="input" type="email" formControlName="supportEmail" />
            @if (err(general.controls.supportEmail); as e) { <p class="field-error">{{ e }}</p> }
          </div>
        </div>
        <div class="field max-w-xs">
          <label class="field-label" for="grace">Días de gracia tras el vencimiento</label>
          <input id="grace" class="input type-numeric" type="number" min="0" max="30" formControlName="saasGraceDays" />
          <p class="field-hint">Durante la gracia el vendedor sigue vendiendo; después, su panel queda en solo lectura y su tienda cerrada.</p>
          @if (err(general.controls.saasGraceDays); as e) { <p class="field-error">{{ e }}</p> }
        </div>
        <button type="submit" class="btn btn-primary justify-self-start" [disabled]="busy()">Guardar</button>
      </form>

      <section class="mt-14 grid max-w-3xl gap-10" aria-labelledby="cobro-titulo">
        <div>
          <h2 id="cobro-titulo" class="type-h2">Cuentas donde pagan los vendedores</h2>
          <p class="mt-3">Son las cuentas de la plataforma, distintas de las de cada vendedor. Se muestran con su titular declarado en «Mi plan».</p>
        </div>
        @for (m of s.paymentMethods; track m.method) {
          <form class="grid gap-5 border-t border-[var(--line-subtle)] pt-8" [formGroup]="methodForms[m.method]" (ngSubmit)="saveMethod(m.method)" novalidate>
            <h3 class="font-[family-name:var(--font-editorial)] text-2xl font-semibold text-night">{{ m.methodLabel }}</h3>
            <div class="grid gap-5 sm:grid-cols-2">
              <div class="field">
                <label class="field-label" [for]="m.method + '-holder'">Titular declarado</label>
                <input [id]="m.method + '-holder'" class="input" formControlName="holderName" />
                @if (err(methodForms[m.method].controls.holderName); as e) { <p class="field-error">{{ e }}</p> }
              </div>
              <div class="field">
                <label class="field-label" [for]="m.method + '-phone'">Celular</label>
                <input [id]="m.method + '-phone'" class="input" type="tel" formControlName="phone" placeholder="987 654 321" />
                @if (err(methodForms[m.method].controls.phone); as e) { <p class="field-error">{{ e }}</p> }
              </div>
            </div>
            <label class="flex gap-3"><input type="checkbox" class="checkbox" formControlName="enabled" /> Mostrar a los vendedores para pagar su plan</label>
            <button type="submit" class="btn btn-primary justify-self-start" [disabled]="busy()">Guardar {{ m.methodLabel }}</button>
            <div>
              <p class="field-label mb-3">Código QR (opcional)</p>
              <app-image-upload label="QR" noun="código QR" [imageUrl]="m.qrImageUrl" [busy]="busy()" [disabled]="!m.holderName"
                                (selected)="uploadQr(m.method, $event)" (remove)="removeQr(m.method)" />
            </div>
          </form>
        }
      </section>
    } @else if (!error()) {
      <div class="mt-6 h-56 animate-pulse rounded bg-mist/25" aria-busy="true"></div>
    }
  `,
})
export class AdminSettingsPage {
  private readonly api = inject(AdminApi);
  private readonly fb = inject(NonNullableFormBuilder);
  protected readonly settings = signal<PlatformSettings | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);

  protected readonly general = this.fb.group({
    supportWhatsapp: ['', [peruMobileValidator]],
    supportEmail: ['', [Validators.email, Validators.maxLength(254)]],
    saasGraceDays: [5, [Validators.required, Validators.min(0), Validators.max(30)]],
  });

  protected readonly methodForms: Record<Method, MethodForm> = {
    YAPE: this.methodForm(),
    PLIN: this.methodForm(),
  };

  constructor() {
    this.api.settings().subscribe({ next: (s) => this.apply(s), error: (e: unknown) => this.error.set(ApiError.from(e).message) });
  }

  protected err(control: FormControl): string | null {
    return controlError(control);
  }

  protected saveGeneral(): void {
    if (this.general.invalid) {
      this.general.markAllAsTouched();
      return;
    }
    const v = this.general.getRawValue();
    this.run(
      this.api.updateSettings({ supportWhatsapp: v.supportWhatsapp.trim() || null, supportEmail: v.supportEmail.trim() || null, saasGraceDays: v.saasGraceDays }),
      'Configuración guardada.',
      this.general,
    );
  }

  protected saveMethod(method: Method): void {
    const form = this.methodForms[method];
    if (form.invalid) {
      form.markAllAsTouched();
      return;
    }
    const v = form.getRawValue();
    this.run(this.api.updatePaymentMethod(method, { holderName: v.holderName.trim() || null, phone: v.phone.trim() || null, enabled: v.enabled }), 'Cuenta guardada.', form);
  }

  protected uploadQr(method: Method, file: File): void {
    this.run(this.api.uploadQr(method, file), 'QR actualizado.');
  }

  protected removeQr(method: Method): void {
    this.run(this.api.deleteQr(method), 'QR eliminado.');
  }

  private methodForm(): MethodForm {
    return this.fb.group({
      holderName: ['', [Validators.maxLength(120)]],
      phone: ['', [peruMobileValidator]],
      enabled: [false],
    });
  }

  private run(request: Observable<PlatformSettings>, success: string, form?: FormGroup): void {
    this.busy.set(true);
    this.error.set(null);
    this.notice.set(null);
    request.subscribe({
      next: (s) => {
        this.apply(s);
        this.notice.set(success);
        this.busy.set(false);
      },
      error: (e: unknown) => {
        const apiError = ApiError.from(e);
        if (form) applyServerErrors(form, apiError);
        this.error.set(apiError.message);
        this.busy.set(false);
      },
    });
  }

  private apply(s: PlatformSettings): void {
    this.settings.set(s);
    this.general.reset({ supportWhatsapp: local(s.supportWhatsapp), supportEmail: s.supportEmail ?? '', saasGraceDays: s.saasGraceDays });
    s.paymentMethods.forEach((m: PlatformPaymentMethod) =>
      this.methodForms[m.method].reset({ holderName: m.holderName ?? '', phone: local(m.phone), enabled: m.enabled }),
    );
  }
}

function local(e164: string | null): string {
  return e164 ? e164.replace(/^\+51/, '') : '';
}
