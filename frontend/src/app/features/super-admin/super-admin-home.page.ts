import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { BrandMarkComponent } from '../../shared/components/brand-mark.component';

/** Punto de entrada del SUPER_ADMIN. Los módulos de administración SaaS llegan en la Fase 6. */
@Component({
  selector: 'app-super-admin-home-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [BrandMarkComponent],
  template: `
    <header class="bg-shadow">
      <div class="mx-auto flex max-w-5xl items-center justify-between px-5 py-4 sm:px-8">
        <app-brand-mark tone="light" link="/admin" />
        <button type="button" class="btn btn-ghost-light !min-h-10" (click)="logout()" [disabled]="loggingOut()">Cerrar sesión</button>
      </div>
    </header>
    <main class="mx-auto max-w-5xl px-5 py-14 sm:px-8">
      <p class="type-eyebrow">Superadministración</p>
      <h1 class="type-h1 mt-3">Hola, {{ auth.user()?.fullName }}.</h1>
      <p class="mt-4 max-w-2xl">
        Sesión iniciada como superadministrador. La gestión de vendedores, planes SaaS, cobros SaaS y auditoría
        todavía no está disponible en esta versión.
      </p>
    </main>
  `,
})
export class SuperAdminHomePage {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly loggingOut = signal(false);

  protected logout(): void {
    this.loggingOut.set(true);
    this.auth.logout().subscribe(() => void this.router.navigateByUrl('/ingresar'));
  }
}
