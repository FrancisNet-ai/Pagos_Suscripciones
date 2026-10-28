import { A11yModule } from '@angular/cdk/a11y';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  viewChild,
  computed,
  inject,
  signal,
} from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { StaffPermission } from '../../core/auth/auth.models';
import { AuthService } from '../../core/auth/auth.service';
import { BrandMarkComponent } from '../../shared/components/brand-mark.component';
import { IconComponent } from '../../shared/components/icon.component';
import { formatDate } from '../billing/billing.api';
import { TasksApi } from '../tasks/tasks.api';

interface NavChild {
  label: string;
  /** Sin `route`: aún no implementado, se muestra deshabilitado. */
  route?: string;
  queryParams?: Record<string, string>;
  /** Quién la ve: el titular siempre; un colaborador, si tiene este permiso. `OWNER`: solo el titular. */
  access?: StaffPermission | 'OWNER';
}

interface NavItem {
  icon: string;
  label: string;
  route?: string;
  access?: StaffPermission | 'OWNER';
  children?: NavChild[];
}

@Component({
  selector: 'app-dashboard-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    BrandMarkComponent,
    A11yModule,
    IconComponent,
  ],
  templateUrl: './dashboard-shell.component.html',
})
export class DashboardShellComponent {
  protected readonly auth = inject(AuthService);
  protected readonly tasks = inject(TasksApi);
  private readonly router = inject(Router);
  protected readonly menuOpen = signal(false);
  private readonly menuTrigger = viewChild<ElementRef<HTMLButtonElement>>('menuTrigger');

  protected closeMenu(): void {
    this.menuOpen.set(false);
    this.menuTrigger()?.nativeElement.focus();
  }
  protected readonly loggingOut = signal(false);

  private readonly allNav: NavItem[] = [
    { icon: 'grid', label: 'Inicio', route: '/panel' },
    { icon: 'check', label: 'Pendientes', route: '/panel/pendientes' },
    { icon: 'box', label: 'Productos', route: '/panel/productos', access: 'PRODUCTS' },
    {
      icon: 'users',
      label: 'Clientes',
      children: [
        { label: 'Todos', route: '/panel/clientes', access: 'CUSTOMERS' },
        { label: 'Registrar cliente', route: '/panel/clientes/nuevo', access: 'CUSTOMERS' },
        { label: 'Importar clientes', route: '/panel/clientes/importar', access: 'CUSTOMERS' },
      ],
    },
    {
      icon: 'wallet',
      label: 'Ventas y pagos',
      children: [
        { label: 'Pedidos', route: '/panel/ventas', access: 'ORDERS' },
        {
          label: 'Por verificar',
          route: '/panel/ventas',
          queryParams: { filtro: 'TO_VERIFY' },
          access: 'PAYMENTS',
        },
        { label: 'Reportes', route: '/panel/reportes', access: 'REPORTS' },
      ],
    },
    {
      icon: 'renew',
      label: 'Suscripciones y renovaciones',
      children: [
        { label: 'Suscripciones', route: '/panel/suscripciones', access: 'CUSTOMERS' },
        {
          label: 'Próximos vencimientos',
          route: '/panel/suscripciones',
          queryParams: { filtro: 'EXPIRING' },
          access: 'CUSTOMERS',
        },
        { label: 'Renovaciones', route: '/panel/renovaciones', access: 'RENEWALS' },
      ],
    },
    {
      icon: 'settings',
      label: 'Configuración',
      access: 'OWNER',
      children: [
        { label: 'Datos del negocio', route: '/panel/configuracion/negocio' },
        { label: 'Enlace público', route: '/panel/configuracion/enlace' },
        { label: 'Yape y Plin', route: '/panel/configuracion/cobros' },
        { label: 'Avisos de renovación', route: '/panel/configuracion/renovaciones' },
        { label: 'WhatsApp Business', route: '/panel/configuracion/whatsapp' },
        { label: 'Equipo', route: '/panel/configuracion/equipo' },
        { label: 'Mi plan', route: '/panel/plan' },
      ],
    },
  ];

  /** El menú del colaborador solo muestra las secciones que el titular le habilitó. */
  protected readonly nav = computed<NavItem[]>(() => {
    if (this.auth.isOwner()) return this.allNav;
    const allowed = (access?: StaffPermission | 'OWNER') =>
      !access || (access !== 'OWNER' && this.auth.can(access));
    return this.allNav
      .filter((item) => allowed(item.access))
      .map((item) =>
        item.children ? { ...item, children: item.children.filter((c) => allowed(c.access)) } : item,
      )
      .filter((item) => !item.children || item.children.length > 0);
  });

  /** Aviso persistente sobre el plan SaaS (sin plan, en gracia o por vencer). */
  protected readonly planBanner = computed<{
    tone: 'danger' | 'warning' | 'info';
    text: string;
    action: string;
  } | null>(() => {
    const access = this.auth.business()?.access;
    if (!access || this.url().startsWith('/panel/plan')) return null;
    if (!this.auth.isOwner()) {
      // El colaborador no gestiona el plan: solo se le explica por qué no puede hacer cambios.
      return access.active
        ? null
        : {
            tone: access.block === 'SUSPENDED' ? 'danger' : 'warning',
            text: 'El plan del negocio no está vigente: el panel está en modo de solo lectura. Avísale al titular.',
            action: '',
          };
    }
    if (access.block === 'SUSPENDED')
      return { tone: 'danger', text: access.message ?? '', action: 'Ver detalles' };
    if (!access.active)
      return {
        tone: 'warning',
        text: `${access.message ?? ''} Mientras tanto, el panel está en modo de solo lectura.`,
        action: 'Ir a Mi plan',
      };
    if (access.subscriptionStatus === 'PAST_DUE') {
      return {
        tone: 'warning',
        text: `Tu plan venció. Puedes seguir vendiendo hasta el ${formatDate(access.usableUntil)}.`,
        action: 'Renovar',
      };
    }
    const days = access.expiresAt
      ? Math.ceil((new Date(access.expiresAt).getTime() - Date.now()) / 86_400_000)
      : null;
    if (days !== null && days <= 7) {
      return {
        tone: 'info',
        text: `Tu plan ${access.planName ?? ''} vence el ${formatDate(access.expiresAt)}.`,
        action: 'Renovar',
      };
    }
    return null;
  });

  protected readonly currentSection = computed(
    () =>
      this.nav().find((item) =>
        item.route
          ? this.url() === item.route
          : item.children?.some((child) => child.route && this.url().startsWith(child.route)),
      )?.label ?? 'Tu negocio',
  );

  protected hasRoutes(item: NavItem): boolean {
    return item.children?.some((c) => c.route) ?? false;
  }

  protected isSectionActive(item: NavItem): boolean {
    return item.children?.some((c) => c.route && this.router.url.startsWith(c.route)) ?? false;
  }

  private readonly url = signal(this.router.url);

  constructor() {
    this.router.events
      .pipe(
        filter((e): e is NavigationEnd => e instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe((e) => {
        if (this.menuOpen()) this.closeMenu();
        this.url.set(e.urlAfterRedirects);
        this.tasks.refreshCounts();
      });
  }

  protected initials(): string {
    const name = this.auth.user()?.fullName ?? '';
    return name
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((p) => p[0]!.toUpperCase())
      .join('');
  }

  protected logout(): void {
    this.loggingOut.set(true);
    this.auth.logout().subscribe(() => void this.router.navigateByUrl('/ingresar'));
  }
}
