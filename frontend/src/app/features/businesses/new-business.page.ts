import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { ApiError } from '../../core/services/api-error';
import { applyServerErrors, controlError } from '../../shared/validators/form-errors';

/** Otro negocio con la misma cuenta: tienda, catálogo, clientes y plan propios. */
@Component({
  selector: 'app-new-business-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <section class="mx-auto max-w-2xl">
      <a routerLink="/panel" class="link text-sm">← Volver al panel</a>
      <p class="type-eyebrow mt-6">Tu cuenta</p>
      <h1 class="type-h1 mt-3">Crear otro negocio</h1>
      <p class="mt-3">
        Cada negocio tiene su propia tienda, catálogo, clientes, equipo y plan. Con tu misma cuenta podrás cambiar de
        uno a otro desde el menú. El negocio nuevo empieza sin plan: elige uno en «Mi plan» para empezar a vender.
      </p>
      @if (error(); as e) {
        <p class="notice notice-error mt-6" role="alert">{{ e }}</p>
      }
      <form class="mt-8 grid gap-5" [formGroup]="form" (ngSubmit)="create()" novalidate>
        <div class="field">
          <label class="field-label" for="businessName">Nombre comercial</label>
          <input id="businessName" class="input" formControlName="businessName" autocomplete="organization" />
          @if (err(); as e) {
            <p class="field-error">{{ e }}</p>
          }
        </div>
        <div>
          <button type="submit" class="btn btn-primary" [disabled]="busy()">Crear negocio</button>
        </div>
      </form>
    </section>
  `,
})
export class NewBusinessPage {
  private readonly auth = inject(AuthService);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = inject(NonNullableFormBuilder).group({
    businessName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(120)]],
  });

  protected err(): string | null {
    return controlError(this.form.controls.businessName);
  }

  protected create(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.auth.createBusiness(this.form.controls.businessName.value.trim()).subscribe({
      // Recarga completa para no mezclar datos del negocio anterior en memoria.
      next: () => window.location.assign('/panel/plan'),
      error: (e: unknown) => {
        const apiError = ApiError.from(e);
        applyServerErrors(this.form, apiError);
        this.error.set(apiError.message);
        this.busy.set(false);
      },
    });
  }
}
