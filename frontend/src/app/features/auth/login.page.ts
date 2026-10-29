import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService, isChallenge } from '../../core/auth/auth.service';
import { ApiError } from '../../core/services/api-error';
import { PasswordInputComponent } from '../../shared/components/password-input.component';
import { controlError } from '../../shared/validators/form-errors';
import { AuthLayoutComponent } from './auth-layout.component';

@Component({
  selector: 'app-login-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, AuthLayoutComponent, PasswordInputComponent],
  template: `
    <app-auth-layout>
      <p class="type-eyebrow">Acceso de vendedores</p>
      <h1 class="type-h1 mt-3">Bienvenido de nuevo</h1>
      <p class="mt-3">Ingresa para gestionar tus ventas, clientes y renovaciones.</p>

      @if (motivo() === 'sesion') {
        <p class="notice notice-info mt-6" role="status">Tu sesión expiró. Vuelve a ingresar para continuar.</p>
      }
      @if (error(); as message) {
        <p class="notice notice-error mt-6" role="alert">{{ message }}</p>
      }

      @if (challenge(); as token) {
        <form class="mt-8 grid gap-5" [formGroup]="codeForm" (ngSubmit)="submitCode(token)" novalidate>
          <div>
            <h2 class="text-lg font-semibold text-night">Verificación en dos pasos</h2>
            <p class="mt-2 text-sm">
              @if (useRecovery()) {
                Escribe uno de tus códigos de recuperación. Cada código sirve una sola vez.
              } @else {
                Abre tu app de autenticación y escribe el código de 6 dígitos de Nuvyra.
              }
            </p>
          </div>
          <div class="field">
            <label class="field-label" for="code">{{ useRecovery() ? 'Código de recuperación' : 'Código' }}</label>
            <input id="code" class="input" formControlName="code" autocomplete="one-time-code"
                   [attr.inputmode]="useRecovery() ? 'text' : 'numeric'" [attr.maxlength]="useRecovery() ? 11 : 6"
                   [attr.aria-invalid]="!!codeError()" aria-describedby="code-error" />
            @if (codeError(); as e) { <p id="code-error" class="field-error">{{ e }}</p> }
          </div>
          <button class="btn btn-primary btn-block" type="submit" [disabled]="loading()">
            @if (loading()) { <span class="spinner" aria-hidden="true"></span> Verificando… } @else { Verificar e ingresar }
          </button>
          <div class="flex flex-wrap justify-between gap-3 text-sm">
            <button type="button" class="link" (click)="toggleRecovery()">
              {{ useRecovery() ? 'Usar el código de la app' : 'No tengo mi celular: usar un código de recuperación' }}
            </button>
            <button type="button" class="link" (click)="restart()">Volver</button>
          </div>
        </form>
      } @else {
      <form class="mt-8 grid gap-5" [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <div class="field">
          <label class="field-label" for="email">Correo electrónico</label>
          <input id="email" class="input" type="email" formControlName="email" autocomplete="email" inputmode="email"
                 [attr.aria-invalid]="!!err('email')" aria-describedby="email-error" />
          @if (err('email'); as e) { <p id="email-error" class="field-error">{{ e }}</p> }
        </div>

        <div class="field">
          <div class="flex items-baseline justify-between">
            <label class="field-label" for="password">Contraseña</label>
            <a class="link text-sm" routerLink="/recuperar">¿Olvidaste tu contraseña?</a>
          </div>
          <app-password-input inputId="password" formControlName="password" autocomplete="current-password"
                              [invalid]="!!err('password')" describedBy="password-error" />
          @if (err('password'); as e) { <p id="password-error" class="field-error">{{ e }}</p> }
        </div>

        <button class="btn btn-primary btn-block mt-2" type="submit" [disabled]="loading()">
          @if (loading()) { <span class="spinner" aria-hidden="true"></span> Ingresando… } @else { Ingresar }
        </button>
      </form>
      }

      <hr class="rule my-8" />
      <p class="text-center">¿Aún no tienes cuenta? <a class="link" routerLink="/registro">Crea tu negocio</a></p>
    </app-auth-layout>
  `,
})
export class LoginPage {
  /** Ruta a la que volver tras ingresar (query param `volver`). */
  readonly volver = input<string>();
  readonly motivo = input<string>();

  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  /** Token del desafío cuando la cuenta tiene verificación en dos pasos. */
  protected readonly challenge = signal<string | null>(null);
  protected readonly useRecovery = signal(false);
  protected readonly codeForm = this.fb.group({ code: ['', [Validators.required, Validators.maxLength(20)]] });

  protected readonly form = this.fb.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    password: ['', [Validators.required]],
  });

  protected err(name: 'email' | 'password'): string | null {
    return controlError(this.form.controls[name]);
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.auth.login(this.form.getRawValue()).subscribe({
      next: (body) => {
        if (isChallenge(body)) {
          this.challenge.set(body.challengeToken);
          this.loading.set(false);
          return;
        }
        this.goHome();
      },
      error: (e: unknown) => {
        this.error.set(ApiError.from(e).message);
        this.loading.set(false);
      },
    });
  }

  protected codeError(): string | null {
    return controlError(this.codeForm.controls.code);
  }

  protected submitCode(token: string): void {
    if (this.codeForm.invalid) {
      this.codeForm.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.auth.completeSecondFactor(token, this.codeForm.getRawValue().code).subscribe({
      next: () => this.goHome(),
      error: (e: unknown) => {
        const apiError = ApiError.from(e);
        this.loading.set(false);
        this.codeForm.reset();
        // Desafío vencido o con demasiados intentos: se vuelve a pedir la contraseña.
        if (apiError.status === 401) this.restart();
        this.error.set(apiError.message);
      },
    });
  }

  protected toggleRecovery(): void {
    this.useRecovery.update((v) => !v);
    this.codeForm.reset();
  }

  protected restart(): void {
    this.challenge.set(null);
    this.useRecovery.set(false);
    this.codeForm.reset();
    this.form.controls.password.reset();
    this.error.set(null);
  }

  private goHome(): void {
    void this.router.navigateByUrl(this.safeReturnUrl() ?? this.auth.homeRoute());
  }

  /** Solo se aceptan rutas internas para evitar redirecciones abiertas. */
  private safeReturnUrl(): string | null {
    const url = this.volver();
    return url && url.startsWith('/') && !url.startsWith('//') ? url : null;
  }
}
