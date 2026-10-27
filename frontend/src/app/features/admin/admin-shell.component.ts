import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { BrandMarkComponent } from '../../shared/components/brand-mark.component';

/** Panel del SUPER_ADMIN: separado del panel de vendedores, con navegación propia. */
@Component({
  selector: 'app-admin-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, BrandMarkComponent],
  template: `
    <header class="bg-shadow text-snow">
      <div class="mx-auto flex max-w-6xl items-center justify-between gap-4 px-4 pt-5 sm:px-8">
        <div class="flex items-center gap-4">
          <app-brand-mark tone="light" link="/admin" />
          <span class="hidden border-l border-white/15 pl-4 text-xs uppercase tracking-[0.16em] text-mist sm:inline">Superadministración</span>
        </div>
        <div class="flex items-center gap-3">
          <span class="hidden text-sm text-mist md:inline">{{ auth.user()?.email }}</span>
          <button type="button" class="btn btn-ghost-light !min-h-10" (click)="logout()" [disabled]="loggingOut()">Cerrar sesión</button>
        </div>
      </div>
      <nav class="mx-auto mt-4 flex max-w-6xl gap-1 overflow-x-auto px-2 sm:px-6" aria-label="Superadministración">
        @for (item of nav; track item.route) {
          <a [routerLink]="item.route" routerLinkActive="!border-snow !text-snow" [routerLinkActiveOptions]="{ exact: item.route === '/admin' }"
             class="whitespace-nowrap border-b-2 border-transparent px-3 pb-3 pt-2 text-sm font-semibold text-mist no-underline hover:text-snow">{{ item.label }}</a>
        }
      </nav>
    </header>
    <main class="mx-auto max-w-6xl px-4 py-10 sm:px-8 lg:py-14">
      <router-outlet />
    </main>
  `,
})
export class AdminShellComponent {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly loggingOut = signal(false);

  protected readonly nav = [
    { label: 'Resumen', route: '/admin' },
    { label: 'Pagos SaaS', route: '/admin/pagos' },
    { label: 'Negocios', route: '/admin/negocios' },
    { label: 'Planes', route: '/admin/planes' },
    { label: 'Configuración', route: '/admin/configuracion' },
    { label: 'Auditoría', route: '/admin/auditoria' },
  ];

  protected logout(): void {
    this.loggingOut.set(true);
    this.auth.logout().subscribe(() => void this.router.navigateByUrl('/ingresar'));
  }
}
