import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal, viewChild } from '@angular/core';
import { AbstractControl, NonNullableFormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { AuthService } from '../../core/auth/auth.service';
import { ApiError } from '../../core/services/api-error';
import { PasswordInputComponent } from '../../shared/components/password-input.component';
import { applyServerErrors, controlError } from '../../shared/validators/form-errors';
import { SessionsListComponent } from './sessions-list.component';
import { TwoFactorSettingsComponent } from './two-factor-settings.component';

function matchesNew(control: AbstractControl): ValidationErrors | null {
  const parent = control.parent;
  return parent && control.value !== parent.get('newPassword')?.value ? { passwordMismatch: true } : null;
}

type Field = 'currentPassword' | 'newPassword' | 'confirm';

/** Datos de la cuenta, contraseña, verificación en dos pasos y sesiones abiertas (también para el superadministrador). */
@Component({
  selector: 'app-account-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, PasswordInputComponent, TwoFactorSettingsComponent, SessionsListComponent],
  template: `
    <section class="mx-auto max-w-2xl">
      <p class="type-eyebrow">Tu cuenta</p>
      <h1 class="type-h1 mt-3">{{ auth.user()?.fullName }}</h1>
      <p class="mt-2 text-[var(--text-muted)]">{{ auth.user()?.email }}</p>

      <form class="mt-10 grid gap-5" [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <h2 class="type-h2">Cambiar contraseña</h2>
        <p class="-mt-2 text-sm">
          Al cambiarla cerraremos tus sesiones abiertas en otros dispositivos. Esta sesión sigue abierta.
        </p>
        @if (notice(); as n) {
          <p class="notice notice-success" role="status">{{ n }}</p>
        }
        @if (error(); as e) {
          <p class="notice notice-error" role="alert">{{ e }}</p>
        }
        <div class="field">
          <label class="field-label" for="currentPassword">Contraseña actual</label>
          <app-password-input inputId="currentPassword" formControlName="currentPassword" autocomplete="current-password"
                              [invalid]="!!err('currentPassword')" describedBy="currentPassword-error" />
          @if (err('currentPassword'); as e) { <p id="currentPassword-error" class="field-error">{{ e }}</p> }
        </div>
        <div class="field">
          <label class="field-label" for="newPassword">Nueva contraseña</label>
          <app-password-input inputId="newPassword" formControlName="newPassword" autocomplete="new-password"
                              [invalid]="!!err('newPassword')" describedBy="newPassword-hint newPassword-error" />
          <p id="newPassword-hint" class="field-hint">Mínimo 10 caracteres. Una frase fácil de recordar funciona bien.</p>
          @if (err('newPassword'); as e) { <p id="newPassword-error" class="field-error">{{ e }}</p> }
        </div>
        <div class="field">
          <label class="field-label" for="confirm">Repite la nueva contraseña</label>
          <app-password-input inputId="confirm" formControlName="confirm" autocomplete="new-password"
                              [invalid]="!!err('confirm')" describedBy="confirm-error" />
          @if (err('confirm'); as e) { <p id="confirm-error" class="field-error">{{ e }}</p> }
        </div>
        <div>
          <button type="submit" class="btn btn-primary" [disabled]="busy()">
            @if (busy()) { <span class="spinner" aria-hidden="true"></span> Guardando… } @else { Cambiar contraseña }
          </button>
        </div>
      </form>

      <hr class="rule my-10" />
      <app-two-factor-settings />

      <hr class="rule my-10" />
      <app-sessions-list />
    </section>
  `,
})
export class AccountPage {
  protected readonly auth = inject(AuthService);
  private readonly http = inject(HttpClient);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  private readonly sessionsList = viewChild(SessionsListComponent);

  protected readonly form = inject(NonNullableFormBuilder).group({
    currentPassword: ['', [Validators.required]],
    newPassword: ['', [Validators.required, Validators.minLength(10), Validators.maxLength(72)]],
    confirm: ['', [Validators.required, matchesNew]],
  });

  constructor() {
    this.form.controls.newPassword.valueChanges.subscribe(() => this.form.controls.confirm.updateValueAndValidity());
  }

  protected err(name: Field): string | null {
    return controlError(this.form.controls[name]);
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.busy.set(true);
    this.error.set(null);
    this.notice.set(null);
    this.http
      .post<{ otherSessionsClosed: number }>('/api/v1/auth/password', { currentPassword: v.currentPassword, newPassword: v.newPassword })
      .subscribe({
        next: ({ otherSessionsClosed }) => {
          this.form.reset();
          this.busy.set(false);
          this.sessionsList()?.reload();
          this.notice.set(
            otherSessionsClosed > 0
              ? `Contraseña actualizada. Cerramos ${otherSessionsClosed} ${otherSessionsClosed === 1 ? 'sesión' : 'sesiones'} en otros dispositivos.`
              : 'Contraseña actualizada.',
          );
        },
        error: (e: unknown) => {
          const apiError = ApiError.from(e);
          applyServerErrors(this.form, apiError);
          this.error.set(apiError.message);
          this.busy.set(false);
        },
      });
  }
}
