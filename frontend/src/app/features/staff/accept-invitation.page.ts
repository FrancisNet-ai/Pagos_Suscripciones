import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { ApiError } from '../../core/services/api-error';
import { PasswordInputComponent } from '../../shared/components/password-input.component';
import { applyServerErrors, controlError } from '../../shared/validators/form-errors';
import { peruMobileValidator } from '../../shared/validators/peru-phone';
import { formatDate } from '../billing/billing.api';
import { AuthLayoutComponent } from '../auth/auth-layout.component';
import { PublicInvitation, StaffApi } from './staff.api';

type Field = 'fullName' | 'phone' | 'password' | 'acceptTerms' | 'acceptPrivacy';

/** El colaborador crea su cuenta desde el enlace que le compartió el titular. */
@Component({
  selector: 'app-accept-invitation-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, AuthLayoutComponent, PasswordInputComponent],
  template: `
    <app-auth-layout quote="Un mismo negocio, cada quien en lo suyo." caption="Tu acceso es personal: no compartas tu contraseña.">
      <p class="type-eyebrow">Invitación de equipo</p>
      @if (invitation(); as inv) {
        <h1 class="type-h1 mt-3">Únete a {{ inv.businessName }}</h1>
        <p class="mt-3">
          Te invitaron como colaborador. Podrás usar: {{ permissionText(inv) }}. La invitación vence el
          {{ date(inv.expiresAt) }}.
        </p>

        @if (auth.isAuthenticated()) {
          <p class="notice notice-warning mt-6" role="alert">
            Ya iniciaste sesión como {{ auth.user()?.email }}. Cierra esa sesión para crear el acceso de {{ inv.email }}.
          </p>
          <button type="button" class="btn btn-secondary btn-block mt-4" (click)="logout()">Cerrar sesión</button>
        } @else {
          @if (error(); as message) {
            <p class="notice notice-error mt-6" role="alert">{{ message }}</p>
          }
          <form class="mt-8 grid gap-5" [formGroup]="form" (ngSubmit)="submit()" novalidate>
            <div class="field">
              <label class="field-label" for="inviteEmail">Correo</label>
              <input id="inviteEmail" class="input" type="email" [value]="inv.email" readonly aria-describedby="inviteEmail-hint" />
              <p id="inviteEmail-hint" class="field-hint">Es el correo con el que te invitaron; lo usarás para iniciar sesión.</p>
            </div>
            <div class="field">
              <label class="field-label" for="fullName">Nombre completo</label>
              <input id="fullName" class="input" formControlName="fullName" autocomplete="name"
                     [attr.aria-invalid]="!!err('fullName')" aria-describedby="fullName-error" />
              @if (err('fullName'); as e) { <p id="fullName-error" class="field-error">{{ e }}</p> }
            </div>
            <div class="field">
              <label class="field-label" for="phone">Celular</label>
              <input id="phone" class="input" type="tel" formControlName="phone" autocomplete="tel-national" inputmode="tel"
                     placeholder="987 654 321" [attr.aria-invalid]="!!err('phone')" aria-describedby="phone-error" />
              @if (err('phone'); as e) { <p id="phone-error" class="field-error">{{ e }}</p> }
            </div>
            <div class="field">
              <label class="field-label" for="password">Contraseña</label>
              <app-password-input inputId="password" formControlName="password" autocomplete="new-password"
                                  [invalid]="!!err('password')" describedBy="password-hint password-error" />
              <p id="password-hint" class="field-hint">Mínimo 10 caracteres.</p>
              @if (err('password'); as e) { <p id="password-error" class="field-error">{{ e }}</p> }
            </div>
            <div class="grid gap-3">
              <label class="flex gap-3 text-[0.9375rem]">
                <input class="checkbox" type="checkbox" formControlName="acceptTerms" />
                <span>Acepto las <a class="link" routerLink="/legal/condiciones" target="_blank">condiciones del servicio</a>.</span>
              </label>
              @if (err('acceptTerms'); as e) { <p class="field-error pl-8">{{ e }}</p> }
              <label class="flex gap-3 text-[0.9375rem]">
                <input class="checkbox" type="checkbox" formControlName="acceptPrivacy" />
                <span>He leído la <a class="link" routerLink="/legal/privacidad" target="_blank">política de privacidad</a>.</span>
              </label>
              @if (err('acceptPrivacy'); as e) { <p class="field-error pl-8">{{ e }}</p> }
            </div>
            <button class="btn btn-primary btn-block" type="submit" [disabled]="loading()">
              @if (loading()) { <span class="spinner" aria-hidden="true"></span> Creando tu acceso… } @else { Crear mi acceso }
            </button>
          </form>
        }
      } @else if (loadError(); as message) {
        <h1 class="type-h1 mt-3">Invitación no disponible</h1>
        <p class="notice notice-error mt-6" role="alert">{{ message }}</p>
        <a class="btn btn-secondary btn-block mt-6" routerLink="/ingresar">Ir a iniciar sesión</a>
      } @else {
        <div class="mt-6 h-40 animate-pulse rounded bg-mist/25" aria-busy="true"></div>
      }
    </app-auth-layout>
  `,
})
export class AcceptInvitationPage {
  readonly token = input.required<string>();

  protected readonly auth = inject(AuthService);
  private readonly api = inject(StaffApi);
  private readonly router = inject(Router);
  protected readonly invitation = signal<PublicInvitation | null>(null);
  protected readonly loadError = signal<string | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = inject(NonNullableFormBuilder).group({
    fullName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(120)]],
    phone: ['', [Validators.required, peruMobileValidator]],
    password: ['', [Validators.required, Validators.minLength(10), Validators.maxLength(72)]],
    acceptTerms: [false, [Validators.requiredTrue]],
    acceptPrivacy: [false, [Validators.requiredTrue]],
  });

  constructor() {
    effect(() => {
      this.api.view(this.token()).subscribe({
        next: (inv) => {
          this.invitation.set(inv);
          this.form.controls.fullName.setValue(inv.fullName);
        },
        error: (e: unknown) => this.loadError.set(ApiError.from(e).message),
      });
    });
  }

  protected err(name: Field): string | null {
    return controlError(this.form.controls[name]);
  }

  protected permissionText(inv: PublicInvitation): string {
    return inv.permissions.map((p) => p.label.toLowerCase()).join(', ');
  }

  protected date(iso: string): string {
    return formatDate(iso);
  }

  protected logout(): void {
    this.auth.logout().subscribe();
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.api.accept(this.token(), this.form.getRawValue()).subscribe({
      next: (session) => {
        this.auth.startSession(session);
        void this.router.navigateByUrl('/panel');
      },
      error: (e: unknown) => {
        const apiError = ApiError.from(e);
        this.loading.set(false);
        if (apiError.status === 410) {
          // Usada, cancelada o vencida mientras se completaba el formulario.
          this.invitation.set(null);
          this.loadError.set(apiError.message);
          return;
        }
        applyServerErrors(this.form, apiError);
        this.error.set(apiError.message);
      },
    });
  }
}
