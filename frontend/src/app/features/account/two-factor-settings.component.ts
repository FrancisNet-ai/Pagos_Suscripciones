import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../core/auth/auth.service';
import { ApiError } from '../../core/services/api-error';
import { PasswordInputComponent } from '../../shared/components/password-input.component';
import { applyServerErrors, controlError } from '../../shared/validators/form-errors';
import { AccountApi, TwoFactorSetup, TwoFactorStatus, formatDateTime } from './account.api';

type Step = 'idle' | 'password' | 'scan' | 'codes' | 'disable' | 'regenerate';

/** Activa, desactiva y renueva los códigos de la verificación en dos pasos (app de autenticación). */
@Component({
  selector: 'app-two-factor-settings',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, PasswordInputComponent],
  template: `
    <section class="grid gap-4" aria-labelledby="dos-pasos-titulo">
      <h2 id="dos-pasos-titulo" class="type-h2">Verificación en dos pasos</h2>
      <p class="-mt-2 text-sm">
        Además de tu contraseña, te pediremos un código de una app de autenticación (Google Authenticator, Microsoft
        Authenticator u otra). Así nadie entra a tu cuenta solo con tu contraseña.
      </p>
      @if (status()?.required && !status()?.enabled) {
        <p class="notice notice-warning" role="alert">Tu cuenta de administración debe activarla para usar el panel.</p>
      }
      @if (notice(); as n) { <p class="notice notice-success" role="status">{{ n }}</p> }
      @if (error(); as e) { <p class="notice notice-error" role="alert">{{ e }}</p> }

      @switch (step()) {
        @case ('password') {
          <form class="grid gap-4" [formGroup]="passwordForm" (ngSubmit)="startSetup()" novalidate>
            <div class="field">
              <label class="field-label" for="tfaPassword">Confirma tu contraseña</label>
              <app-password-input inputId="tfaPassword" formControlName="password" autocomplete="current-password"
                                  [invalid]="!!passwordError()" describedBy="tfaPassword-error" />
              @if (passwordError(); as e) { <p id="tfaPassword-error" class="field-error">{{ e }}</p> }
            </div>
            <div class="flex flex-wrap gap-3">
              <button type="submit" class="btn btn-primary" [disabled]="busy()">Continuar</button>
              <button type="button" class="btn btn-secondary" (click)="cancel()">Cancelar</button>
            </div>
          </form>
        }
        @case ('scan') {
          @if (setup(); as s) {
            <ol class="grid gap-4 pl-5 text-sm" style="list-style: decimal">
              <li>
                Escanea este código con tu app de autenticación.
                <img class="mt-3 block h-48 w-48 rounded border border-[var(--border)] bg-white p-2" [src]="s.qrDataUrl"
                     alt="Código QR para agregar Nuvyra a tu app de autenticación" />
                <p class="mt-2">
                  ¿No puedes escanearlo? Escribe esta clave en la app:
                  <code class="mt-1 block select-all font-mono text-base tracking-wider">{{ s.secret }}</code>
                </p>
                <a class="link mt-1 inline-block" [href]="s.otpauthUri">Abrir en la app de este dispositivo</a>
              </li>
              <li>
                <form class="grid gap-3" [formGroup]="codeForm" (ngSubmit)="enable()" novalidate>
                  <label class="field-label" for="tfaCode">Escribe el código de 6 dígitos que muestra la app</label>
                  <input id="tfaCode" class="input max-w-[12rem]" formControlName="code" inputmode="numeric" maxlength="6"
                         autocomplete="one-time-code" [attr.aria-invalid]="!!codeError()" aria-describedby="tfaCode-error" />
                  @if (codeError(); as e) { <p id="tfaCode-error" class="field-error">{{ e }}</p> }
                  <div class="flex flex-wrap gap-3">
                    <button type="submit" class="btn btn-primary" [disabled]="busy()">Activar</button>
                    <button type="button" class="btn btn-secondary" (click)="cancel()">Cancelar</button>
                  </div>
                </form>
              </li>
            </ol>
          }
        }
        @case ('codes') {
          <div class="grid gap-3">
            <p class="notice notice-warning" role="alert">
              Guarda estos códigos de recuperación en un lugar seguro. Cada uno sirve una sola vez si pierdes tu celular,
              y no los volveremos a mostrar.
            </p>
            <ul class="grid grid-cols-2 gap-2 font-mono text-base sm:grid-cols-5" aria-label="Códigos de recuperación">
              @for (c of recoveryCodes(); track c) { <li class="select-all">{{ c }}</li> }
            </ul>
            <div class="flex flex-wrap gap-3">
              <button type="button" class="btn btn-secondary" (click)="copyCodes()">{{ copied() ? 'Copiados' : 'Copiar códigos' }}</button>
              <button type="button" class="btn btn-primary" (click)="finishCodes()">Ya los guardé</button>
            </div>
          </div>
        }
        @case ('disable') {
          <form class="grid gap-4" [formGroup]="disableForm" (ngSubmit)="disable()" novalidate>
            <div class="field">
              <label class="field-label" for="disPassword">Contraseña</label>
              <app-password-input inputId="disPassword" formControlName="password" autocomplete="current-password"
                                  [invalid]="!!disableError('password')" describedBy="disPassword-error" />
              @if (disableError('password'); as e) { <p id="disPassword-error" class="field-error">{{ e }}</p> }
            </div>
            <div class="field">
              <label class="field-label" for="disCode">Código de la app o de recuperación</label>
              <input id="disCode" class="input max-w-[14rem]" formControlName="code" autocomplete="one-time-code"
                     [attr.aria-invalid]="!!disableError('code')" aria-describedby="disCode-error" />
              @if (disableError('code'); as e) { <p id="disCode-error" class="field-error">{{ e }}</p> }
            </div>
            <div class="flex flex-wrap gap-3">
              <button type="submit" class="btn btn-secondary" [disabled]="busy()">Desactivar</button>
              <button type="button" class="btn btn-secondary" (click)="cancel()">Cancelar</button>
            </div>
          </form>
        }
        @case ('regenerate') {
          <form class="grid gap-3" [formGroup]="codeForm" (ngSubmit)="regenerate()" novalidate>
            <label class="field-label" for="regCode">Código de 6 dígitos de tu app</label>
            <input id="regCode" class="input max-w-[12rem]" formControlName="code" inputmode="numeric" maxlength="6"
                   autocomplete="one-time-code" [attr.aria-invalid]="!!codeError()" aria-describedby="regCode-error" />
            @if (codeError(); as e) { <p id="regCode-error" class="field-error">{{ e }}</p> }
            <p class="text-sm">Los códigos de recuperación anteriores dejarán de funcionar.</p>
            <div class="flex flex-wrap gap-3">
              <button type="submit" class="btn btn-primary" [disabled]="busy()">Generar códigos nuevos</button>
              <button type="button" class="btn btn-secondary" (click)="cancel()">Cancelar</button>
            </div>
          </form>
        }
        @default {
          @if (status(); as st) {
            @if (st.enabled) {
              <p>
                <strong>Activa</strong> desde el {{ date(st.enabledAt!) }}. Te quedan {{ st.recoveryCodesLeft }}
                {{ st.recoveryCodesLeft === 1 ? 'código' : 'códigos' }} de recuperación.
              </p>
              <div class="flex flex-wrap gap-3">
                <button type="button" class="btn btn-secondary" (click)="go('regenerate')">Generar códigos de recuperación nuevos</button>
                @if (!st.required) {
                  <button type="button" class="btn btn-secondary" (click)="go('disable')">Desactivar</button>
                }
              </div>
            } @else {
              <div><button type="button" class="btn btn-primary" (click)="go('password')">Activar verificación en dos pasos</button></div>
            }
          }
        }
      }
    </section>
  `,
})
export class TwoFactorSettingsComponent {
  private readonly api = inject(AccountApi);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly status = signal<TwoFactorStatus | null>(null);
  protected readonly step = signal<Step>('idle');
  protected readonly setup = signal<TwoFactorSetup | null>(null);
  protected readonly recoveryCodes = signal<string[]>([]);
  protected readonly busy = signal(false);
  protected readonly copied = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);

  protected readonly passwordForm = this.fb.group({ password: ['', [Validators.required]] });
  protected readonly codeForm = this.fb.group({ code: ['', [Validators.required, Validators.pattern(/^\s*\d{6}\s*$/)]] });
  protected readonly disableForm = this.fb.group({
    password: ['', [Validators.required]],
    code: ['', [Validators.required, Validators.maxLength(20)]],
  });

  constructor() {
    this.reload();
  }

  protected passwordError(): string | null {
    return controlError(this.passwordForm.controls.password);
  }

  protected codeError(): string | null {
    const control = this.codeForm.controls.code;
    return control.hasError('pattern') && control.touched ? 'Escribe los 6 dígitos del código' : controlError(control);
  }

  protected disableError(name: 'password' | 'code'): string | null {
    return controlError(this.disableForm.controls[name]);
  }

  protected date(iso: string): string {
    return formatDateTime(iso);
  }

  protected go(step: Step): void {
    this.error.set(null);
    this.notice.set(null);
    this.passwordForm.reset();
    this.codeForm.reset();
    this.disableForm.reset();
    this.step.set(step);
  }

  protected cancel(): void {
    this.setup.set(null);
    this.go('idle');
  }

  protected startSetup(): void {
    if (this.invalid(this.passwordForm)) return;
    this.run(this.api.startTwoFactorSetup(this.passwordForm.getRawValue().password), this.passwordForm, (s) => {
      this.setup.set(s);
      this.step.set('scan');
    });
  }

  protected enable(): void {
    if (this.invalid(this.codeForm)) return;
    this.run(this.api.enableTwoFactor(this.codeForm.getRawValue().code.trim()), this.codeForm, (r) => {
      this.setup.set(null);
      this.recoveryCodes.set(r.recoveryCodes);
      this.auth.setTwoFactorEnabled(true);
      this.step.set('codes');
      this.notice.set(
        r.otherSessionsClosed > 0
          ? `Verificación activada. Cerramos ${r.otherSessionsClosed} ${r.otherSessionsClosed === 1 ? 'sesión' : 'sesiones'} en otros dispositivos.`
          : 'Verificación activada.',
      );
    });
  }

  protected disable(): void {
    if (this.invalid(this.disableForm)) return;
    const v = this.disableForm.getRawValue();
    this.run(this.api.disableTwoFactor(v.password, v.code), this.disableForm, () => {
      this.auth.setTwoFactorEnabled(false);
      this.go('idle');
      this.notice.set('Verificación en dos pasos desactivada.');
      this.reload();
    });
  }

  protected regenerate(): void {
    if (this.invalid(this.codeForm)) return;
    this.run(this.api.regenerateRecoveryCodes(this.codeForm.getRawValue().code.trim()), this.codeForm, (r) => {
      this.recoveryCodes.set(r.recoveryCodes);
      this.step.set('codes');
    });
  }

  protected copyCodes(): void {
    void navigator.clipboard?.writeText(this.recoveryCodes().join('\n')).then(() => this.copied.set(true));
  }

  protected finishCodes(): void {
    this.recoveryCodes.set([]);
    this.copied.set(false);
    this.step.set('idle');
    this.reload();
  }

  private reload(): void {
    this.api.twoFactorStatus().subscribe({ next: (s) => this.status.set(s), error: (e: unknown) => this.error.set(ApiError.from(e).message) });
  }

  private invalid(form: { invalid: boolean; markAllAsTouched(): void }): boolean {
    if (form.invalid) form.markAllAsTouched();
    return form.invalid;
  }

  private run<T>(request: import('rxjs').Observable<T>, form: Parameters<typeof applyServerErrors>[0], done: (value: T) => void): void {
    this.busy.set(true);
    this.error.set(null);
    request.subscribe({
      next: (value) => {
        this.busy.set(false);
        done(value);
      },
      error: (e: unknown) => {
        const apiError = ApiError.from(e);
        applyServerErrors(form, apiError);
        if (!apiError.fieldErrors.length) this.error.set(apiError.message);
        this.busy.set(false);
      },
    });
  }
}
