import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { StepperComponent } from '../../shared/components/stepper.component';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, of } from 'rxjs';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { ApiError } from '../../core/services/api-error';
import { PasswordInputComponent } from '../../shared/components/password-input.component';
import { peruMobileValidator } from '../../shared/validators/peru-phone';
import { applyServerErrors, controlError } from '../../shared/validators/form-errors';
import { AuthLayoutComponent } from './auth-layout.component';
import { BillingApi, Plan } from '../billing/billing.api';
import { PlanCardComponent } from '../billing/plan-card.component';
import { RailDotsComponent } from '../../shared/components/rail-dots.component';

type Field = 'fullName' | 'email' | 'phone' | 'password' | 'businessName' | 'acceptTerms' | 'acceptPrivacy';

@Component({
  selector: 'app-register-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, AuthLayoutComponent, PasswordInputComponent, PlanCardComponent, RailDotsComponent, StepperComponent],
  templateUrl: './register.page.html',
})
export class RegisterPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly loading = signal(false);
  /** 1: tus datos y tu negocio; 2: tu plan. El pago (paso 3) se hace en «Mi plan», ya con la cuenta creada. */
  protected readonly step = signal<1 | 2>(1);
  protected readonly steps = ['Tus datos', 'Tu plan', 'Pago'];
  private readonly firstStep: Field[] = ['fullName', 'email', 'phone', 'password', 'businessName'];
  protected readonly error = signal<string | null>(null);

  protected readonly form = this.fb.group({
    fullName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(120)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    phone: ['', [Validators.required, peruMobileValidator]],
    password: ['', [Validators.required, Validators.minLength(10), Validators.maxLength(72)]],
    businessName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(120)]],
    acceptTerms: [false, [Validators.requiredTrue]],
    acceptPrivacy: [false, [Validators.requiredTrue]],
    planId: [inject(ActivatedRoute).snapshot.queryParamMap.get('plan') ?? ''],
  });

  protected readonly plans = toSignal(inject(BillingApi).publicPlans().pipe(catchError(() => of<Plan[]>([]))));

  protected selectPlan(id: string): void {
    this.form.controls.planId.setValue(this.form.controls.planId.value === id ? '' : id);
  }

  /** Valida solo los datos del primer paso antes de mostrar los planes. */
  protected next(): void {
    const invalid = this.firstStep.filter((f) => this.form.controls[f].invalid);
    if (invalid.length > 0) {
      invalid.forEach((f) => this.form.controls[f].markAsTouched());
      document.getElementById(invalid[0]!)?.focus();
      return;
    }
    this.error.set(null);
    this.step.set(2);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  protected back(): void {
    this.step.set(1);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

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
    const { planId, ...data } = this.form.getRawValue();
    // Un plan que ya no se ofrece (enlace antiguo) no se envía.
    const chosen = this.plans()?.some((p) => p.id === planId) ? planId : null;
    this.auth.register({ ...data, planId: chosen }).subscribe({
      next: () => void this.router.navigateByUrl(chosen ? '/panel/plan?paso=pago' : '/panel'),
      error: (e: unknown) => {
        const apiError = ApiError.from(e);
        applyServerErrors(this.form, apiError);
        this.error.set(apiError.message);
        // Un correo ya registrado u otro dato del primer paso: volver a mostrarlo.
        if (this.firstStep.some((f) => this.form.controls[f].invalid)) this.step.set(1);
        this.loading.set(false);
      },
    });
  }
}
