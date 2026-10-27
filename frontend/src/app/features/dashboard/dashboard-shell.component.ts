import { A11yModule } from '@angular/cdk/a11y';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AuthService } from '../../core/auth/auth.service';
import { BrandMarkComponent } from '../../shared/components/brand-mark.component';
import { formatDate } from '../billing/billing.api';

interface NavChild {
  label: string;
  /** Sin `route`: aún no implementado, se muestra deshabilitado. */
  route?: string;
  queryParams?: Record<string, string>;
}

interface NavItem {
  label: string;
  route?: string;
  children?: NavChild[];
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
    { label: 'Productos', route: '/panel/productos' },
    {
      label: 'Clientes',
      children: [
        { label: 'Todos', route: '/panel/clientes' },
        { label: 'Registrar cliente', route: '/panel/clientes/nuevo' },
        { label: 'Importar clientes', route: '/panel/clientes/importar' },
      ],
    },
    {
      label: 'Ventas y pagos',
      children: [
        { label: 'Pedidos', route: '/panel/ventas' },
        { label: 'Por verificar', route: '/panel/ventas', queryParams: { filtro: 'TO_VERIFY' } },
        { label: 'Reportes', route: '/panel/reportes' },
      ],
    },
    {
      label: 'Suscripciones y renovaciones',
      children: [
        { label: 'Suscripciones', route: '/panel/suscripciones' },
        { label: 'Próximos vencimientos', route: '/panel/suscripciones', queryParams: { filtro: 'EXPIRING' } },
        { label: 'Renovaciones', route: '/panel/renovaciones' },
      ],
    },
    {
      label: 'Configuración',
      children: [
        { label: 'Datos del negocio', route: '/panel/configuracion/negocio' },
        { label: 'Enlace público', route: '/panel/configuracion/enlace' },
        { label: 'Yape y Plin', route: '/panel/configuracion/cobros' },
        { label: 'Avisos de renovación', route: '/panel/configuracion/renovaciones' },
        { label: 'WhatsApp Business', route: '/panel/configuracion/whatsapp' },
        { label: 'Mi plan', route: '/panel/plan' },
      ],
    },
  ];

  /** Aviso persistente sobre el plan SaaS (sin plan, en gracia o por vencer). */
  protected readonly planBanner = computed<{ tone: 'danger' | 'warning' | 'info'; text: string; action: string } | null>(() => {
    const access = this.auth.business()?.access;
    if (!access || this.url().startsWith('/panel/plan')) return null;
    if (access.block === 'SUSPENDED') return { tone: 'danger', text: access.message ?? '', action: 'Ver detalles' };
    if (!access.active) return { tone: 'warning', text: `${access.message ?? ''} Mientras tanto, el panel está en modo de solo lectura.`, action: 'Ir a Mi plan' };
    if (access.subscriptionStatus === 'PAST_DUE') {
      return { tone: 'warning', text: `Tu plan venció. Puedes seguir vendiendo hasta el ${formatDate(access.usableUntil)}.`, action: 'Renovar' };
    }
    const days = access.expiresAt ? Math.ceil((new Date(access.expiresAt).getTime() - Date.now()) / 86_400_000) : null;
    if (days !== null && days <= 7) {
      return { tone: 'info', text: `Tu plan ${access.planName ?? ''} vence el ${formatDate(access.expiresAt)}.`, action: 'Renovar' };
    }
    return null;
  });

  protected hasRoutes(item: NavItem): boolean {
    return item.children?.some((c) => c.route) ?? false;
  }

  protected isSectionActive(item: NavItem): boolean {
    return item.children?.some((c) => c.route && this.router.url.startsWith(c.route)) ?? false;
  }

  private readonly url = signal(this.router.url);

  constructor() {
    this.router.events
      .pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd), takeUntilDestroyed())
      .subscribe((e) => {
        this.menuOpen.set(false);
        this.url.set(e.urlAfterRedirects);
      });
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
