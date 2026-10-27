import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { ApiError } from '../../core/services/api-error';
import { PasswordInputComponent } from '../../shared/components/password-input.component';
import { peruMobileValidator } from '../../shared/validators/peru-phone';
import { applyServerErrors, controlError } from '../../shared/validators/form-errors';
import { AuthLayoutComponent } from './auth-layout.component';

type Field = 'fullName' | 'email' | 'phone' | 'password' | 'businessName' | 'acceptTerms' | 'acceptPrivacy';

@Component({
  selector: 'app-register-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, AuthLayoutComponent, PasswordInputComponent],
  templateUrl: './register.page.html',
})
export class RegisterPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = this.fb.group({
    fullName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(120)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    phone: ['', [Validators.required, peruMobileValidator]],
    password: ['', [Validators.required, Validators.minLength(10), Validators.maxLength(72)]],
    businessName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(120)]],
    acceptTerms: [false, [Validators.requiredTrue]],
    acceptPrivacy: [false, [Validators.requiredTrue]],
  });

  protected err(name: Field): string | null {
    return controlError(this.form.controls[name]);
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.auth.register(this.form.getRawValue()).subscribe({
      next: () => void this.router.navigateByUrl('/panel'),
      error: (e: unknown) => {
        const apiError = ApiError.from(e);
        applyServerErrors(this.form, apiError);
        this.error.set(apiError.message);
        this.loading.set(false);
      },
    });
  }
}
