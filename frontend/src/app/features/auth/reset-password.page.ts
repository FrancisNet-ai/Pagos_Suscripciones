import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { AbstractControl, NonNullableFormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { ApiError } from '../../core/services/api-error';
import { PasswordInputComponent } from '../../shared/components/password-input.component';
import { applyServerErrors, controlError } from '../../shared/validators/form-errors';
import { AuthLayoutComponent } from './auth-layout.component';

function matchesPassword(control: AbstractControl): ValidationErrors | null {
  const parent = control.parent;
  return parent && control.value !== parent.get('newPassword')?.value ? { passwordMismatch: true } : null;
}

@Component({
  selector: 'app-reset-password-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, AuthLayoutComponent, PasswordInputComponent],
  template: `
    <app-auth-layout>
      <p class="type-eyebrow">Recuperar acceso</p>
      <h1 class="type-h1 mt-3">Crea una nueva contraseña</h1>

      @if (!token()) {
        <p class="notice notice-error mt-8" role="alert">El enlace no es válido. Solicita uno nuevo.</p>
        <a class="btn btn-secondary btn-block mt-6" routerLink="/recuperar">Solicitar enlace</a>
      } @else if (done()) {
        <p class="notice notice-success mt-8" role="status">Tu contraseña se actualizó. Por seguridad cerramos tus sesiones abiertas.</p>
        <a class="btn btn-primary btn-block mt-6" routerLink="/ingresar">Iniciar sesión</a>
      } @else {
        @if (error(); as message) {
          <p class="notice notice-error mt-6" role="alert">{{ message }}</p>
        }
        <form class="mt-8 grid gap-5" [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <div class="field">
            <label class="field-label" for="newPassword">Nueva contraseña</label>
            <app-password-input inputId="newPassword" formControlName="newPassword" autocomplete="new-password"
                                [invalid]="!!err('newPassword')" describedBy="newPassword-error" />
            @if (err('newPassword'); as e) { <p id="newPassword-error" class="field-error">{{ e }}</p> }
          </div>
          <div class="field">
            <label class="field-label" for="confirm">Repite la contraseña</label>
            <app-password-input inputId="confirm" formControlName="confirm" autocomplete="new-password"
                                [invalid]="!!err('confirm')" describedBy="confirm-error" />
            @if (err('confirm'); as e) { <p id="confirm-error" class="field-error">{{ e }}</p> }
          </div>
          <button class="btn btn-primary btn-block" type="submit" [disabled]="loading()">
            @if (loading()) { <span class="spinner" aria-hidden="true"></span> Guardando… } @else { Guardar contraseña }
          </button>
        </form>
      }
    </app-auth-layout>
  `,
})
export class ResetPasswordPage {
  readonly token = input<string>();

  private readonly auth = inject(AuthService);
  protected readonly loading = signal(false);
  protected readonly done = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = inject(NonNullableFormBuilder).group({
    newPassword: ['', [Validators.required, Validators.minLength(10), Validators.maxLength(72)]],
    confirm: ['', [Validators.required, matchesPassword]],
  });

  constructor() {
    this.form.controls.newPassword.valueChanges.subscribe(() => this.form.controls.confirm.updateValueAndValidity());
  }

  protected err(name: 'newPassword' | 'confirm'): string | null {
    return controlError(this.form.controls[name]);
  }

  protected submit(): void {
    const token = this.token();
    if (!token || this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.auth.confirmPasswordReset(token, this.form.controls.newPassword.value).subscribe({
      next: () => this.done.set(true),
      error: (e: unknown) => {
        const apiError = ApiError.from(e);
        // p. ej. una contraseña filtrada: el motivo aparece bajo el campo.
        applyServerErrors(this.form, apiError);
        this.error.set(apiError.fieldErrors.length ? null : apiError.message);
        this.loading.set(false);
      },
    });
  }
}
