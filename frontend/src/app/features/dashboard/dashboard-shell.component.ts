import { A11yModule } from '@angular/cdk/a11y';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AuthService } from '../../core/auth/auth.service';
import { BrandMarkComponent } from '../../shared/components/brand-mark.component';

interface NavItem {
  label: string;
  route?: string;
  /** Las secciones sin `route` aún no están implementadas y se muestran deshabilitadas. */
  children?: string[];
}

@Component({
  selector: 'app-dashboard-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, BrandMarkComponent, A11yModule],
  templateUrl: './dashboard-shell.component.html',
})
export class DashboardShellComponent {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly menuOpen = signal(false);
  protected readonly loggingOut = signal(false);

  protected readonly nav: NavItem[] = [
    { label: 'Inicio', route: '/panel' },
    { label: 'Productos', children: ['Catálogo', 'Cupos'] },
    { label: 'Clientes', children: ['Todos', 'Registrar cliente'] },
    { label: 'Ventas y pagos', children: ['Pedidos', 'Por verificar'] },
    { label: 'Suscripciones y renovaciones', children: ['Próximos vencimientos', 'Renovaciones'] },
    { label: 'Configuración', children: ['Negocio', 'Yape y Plin', 'Plan'] },
  ];

  constructor() {
    this.router.events
      .pipe(filter((e) => e instanceof NavigationEnd), takeUntilDestroyed())
      .subscribe(() => this.menuOpen.set(false));
  }

  protected initials(): string {
    const name = this.auth.user()?.fullName ?? '';
    return name.split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0]!.toUpperCase()).join('');
  }

  protected logout(): void {
    this.loggingOut.set(true);
    this.auth.logout().subscribe(() => void this.router.navigateByUrl('/ingresar'));
  }
}
