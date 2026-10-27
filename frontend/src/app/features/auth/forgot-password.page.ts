import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { ApiError } from '../../core/services/api-error';
import { controlError } from '../../shared/validators/form-errors';
import { AuthLayoutComponent } from './auth-layout.component';

@Component({
  selector: 'app-forgot-password-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, AuthLayoutComponent],
  template: `
    <app-auth-layout>
      <p class="type-eyebrow">Recuperar acceso</p>
      <h1 class="type-h1 mt-3">¿Olvidaste tu contraseña?</h1>

      @if (sent()) {
        <p class="notice notice-success mt-8" role="status">
          Si existe una cuenta con ese correo, te enviamos un enlace para restablecer tu contraseña. Revisa también tu carpeta de spam.
        </p>
        <a class="btn btn-secondary btn-block mt-6" routerLink="/ingresar">Volver a iniciar sesión</a>
      } @else {
        <p class="mt-3">Escribe el correo de tu cuenta y te enviaremos un enlace válido por 30 minutos.</p>
        @if (error(); as message) {
          <p class="notice notice-error mt-6" role="alert">{{ message }}</p>
        }
        <form class="mt-8 grid gap-5" [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <div class="field">
            <label class="field-label" for="email">Correo electrónico</label>
            <input id="email" class="input" type="email" formControlName="email" autocomplete="email" inputmode="email"
                   [attr.aria-invalid]="!!emailError()" aria-describedby="email-error" />
            @if (emailError(); as e) { <p id="email-error" class="field-error">{{ e }}</p> }
          </div>
          <button class="btn btn-primary btn-block" type="submit" [disabled]="loading()">
            @if (loading()) { <span class="spinner" aria-hidden="true"></span> Enviando… } @else { Enviar enlace }
          </button>
        </form>
        <p class="mt-8 text-center"><a class="link" routerLink="/ingresar">Volver a iniciar sesión</a></p>
      }
    </app-auth-layout>
  `,
})
export class ForgotPasswordPage {
  private readonly auth = inject(AuthService);
  protected readonly loading = signal(false);
  protected readonly sent = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
  });

  protected emailError(): string | null {
    return controlError(this.form.controls.email);
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.auth.requestPasswordReset(this.form.controls.email.value).subscribe({
      next: () => this.sent.set(true),
      error: (e: unknown) => {
        this.error.set(ApiError.from(e).message);
        this.loading.set(false);
      },
    });
  }
}
