import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  viewChild,
  inject,
  signal,
} from '@angular/core';
import { A11yModule } from '@angular/cdk/a11y';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { BrandMarkComponent } from '../../shared/components/brand-mark.component';
import { IconComponent } from '../../shared/components/icon.component';

@Component({
  selector: 'app-admin-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    BrandMarkComponent,
    IconComponent,
    A11yModule,
  ],
  template: `
    <a
      href="#admin-contenido"
      class="sr-only-focusable fixed left-4 top-4 z-[60] rounded bg-white p-3 text-night"
      >Saltar al contenido</a
    >
    <div class="workspace">
      <header class="workspace-mobile-bar">
        <app-brand-mark tone="light" link="/admin" /><button
          type="button"
          #menuTrigger
          aria-controls="admin-nav"
          [attr.aria-expanded]="menuOpen()"
          (click)="menuOpen.set(true)"
        >
          <app-icon name="menu" />Menú
        </button>
      </header>
      @if (menuOpen()) {
        <div class="workspace-backdrop" (click)="closeMenu()" aria-hidden="true"></div>
      }
      <aside
        id="admin-nav"
        [attr.role]="menuOpen() ? 'dialog' : null"
        [attr.aria-modal]="menuOpen() ? true : null"
        class="workspace-sidebar"
        [class.is-closed]="!menuOpen()"
        aria-label="Navegación de superadministración"
        [cdkTrapFocus]="menuOpen()"
        [cdkTrapFocusAutoCapture]="menuOpen()"
        (keydown.escape)="closeMenu()"
      >
        <div class="sidebar-brand">
          <app-brand-mark tone="light" link="/admin" /><button
            class="sidebar-close"
            type="button"
            aria-label="Cerrar menú"
            (click)="closeMenu()"
          >
            <app-icon name="close" />
          </button>
        </div>
        <div class="sidebar-business">
          <p>Administración central</p>
          <p>Nuvyra · Superadministración</p>
        </div>
        <nav class="sidebar-nav" aria-label="Superadministración">
          <ul>
            @for (item of nav; track item.route) {
              <li>
                <a
                  [routerLink]="item.route"
                  routerLinkActive="active"
                  ariaCurrentWhenActive="page"
                  [routerLinkActiveOptions]="{ exact: item.route === '/admin' }"
                  class="sidebar-link"
                  ><app-icon [name]="item.icon" /><span>{{ item.label }}</span></a
                >
              </li>
            }
          </ul>
        </nav>
        <div class="sidebar-profile">
          <div class="sidebar-profile-row">
            <span class="profile-avatar" aria-hidden="true">N</span>
            <div class="min-w-0">
              <p>{{ auth.user()?.fullName }}</p>
              <p>SUPER_ADMIN</p>
            </div>
          </div>
          <a routerLink="/admin/cuenta" class="sidebar-new-business">Mi cuenta y contraseña</a>
          <button class="btn" type="button" (click)="logout()" [disabled]="loggingOut()">
            <app-icon name="logout" />Cerrar sesión
          </button>
        </div>
      </aside>
      <div class="workspace-body">
        <div class="workspace-topbar">
          <p class="workspace-context">
            <strong>Nuvyra</strong><span class="context-divider">/</span
            ><span class="context-name">Superadministración</span>
          </p>
          <span class="text-xs text-[var(--text-muted)]">Cobros del SaaS</span>
        </div>
        <main id="admin-contenido" class="workspace-main"><router-outlet /></main>
      </div>
    </div>
  `,
})
export class AdminShellComponent {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly loggingOut = signal(false);
  protected readonly menuOpen = signal(false);
  private readonly menuTrigger = viewChild<ElementRef<HTMLButtonElement>>('menuTrigger');
  protected closeMenu(): void {
    this.menuOpen.set(false);
    this.menuTrigger()?.nativeElement.focus();
  }
  protected readonly nav = [
    { label: 'Resumen', route: '/admin', icon: 'grid' },
    { label: 'Pagos SaaS', route: '/admin/pagos', icon: 'wallet' },
    { label: 'Negocios', route: '/admin/negocios', icon: 'users' },
    { label: 'Planes', route: '/admin/planes', icon: 'box' },
    { label: 'Configuración', route: '/admin/configuracion', icon: 'settings' },
    { label: 'Auditoría', route: '/admin/auditoria', icon: 'file' },
  ];
  constructor() {
    this.router.events
      .pipe(
        filter((event) => event instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => {
        if (this.menuOpen()) this.closeMenu();
      });
  }
  protected logout(): void {
    this.loggingOut.set(true);
    this.auth.logout().subscribe(() => void this.router.navigateByUrl('/ingresar'));
  }
}
