import { LoadingIndicatorComponent } from '../../shared/components/loading-indicator.component';
import { IconComponent } from '../../shared/components/icon.component';
import { Clipboard } from '@angular/cdk/clipboard';
import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, forkJoin, map, of, startWith } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { ApiError } from '../../core/services/api-error';
import { SolesPipe } from '../../shared/pipes/soles.pipe';
import { Dashboard, DayAmount, ReportsApi } from '../reports/reports.api';
import { Business, SettingsApi } from '../settings/settings.api';
import { RevenueChartComponent } from './revenue-chart.component';

interface Setup {
  paymentConfigured: boolean;
  publishedProducts: number;
}

type LoadState =
  | { status: 'loading' }
  | { status: 'error'; message: string }
  | { status: 'ready'; business: Business; setup: Setup; dashboard: Dashboard };

@Component({
  selector: 'app-dashboard-home-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LoadingIndicatorComponent, RouterLink, SolesPipe, RevenueChartComponent, IconComponent],
  templateUrl: './dashboard-home.page.html',
  styleUrl: './dashboard-home.page.css',
})
export class DashboardHomePage {
  private readonly http = inject(HttpClient);
  private readonly clipboard = inject(Clipboard);
  protected readonly auth = inject(AuthService);
  protected readonly copied = signal(false);
  protected readonly period = signal<7 | 30>(30);

  protected readonly state = toSignal(
    forkJoin({
      business: inject(SettingsApi).getBusiness(),
      methods: this.http.get<{ enabled: boolean }[]>('/api/v1/business/me/payment-methods'),
      published: this.http.get<{ totalItems: number }>('/api/v1/products', {
        params: { filter: 'PUBLISHED', size: 1 },
      }),
      dashboard: inject(ReportsApi).dashboard(),
    }).pipe(
      map(({ business, methods, published, dashboard }): LoadState => ({
        status: 'ready',
        business,
        dashboard,
        setup: {
          paymentConfigured: methods.some((m) => m.enabled),
          publishedProducts: published.totalItems,
        },
      })),
      catchError((e: unknown) =>
        of<LoadState>({ status: 'error', message: ApiError.from(e).message }),
      ),
      startWith<LoadState>({ status: 'loading' }),
    ),
    { requireSync: true },
  );

  protected readonly firstName = computed(() => this.auth.user()?.fullName.split(' ')[0] ?? '');

  protected readonly chartDays = computed<DayAmount[]>(() => {
    const s = this.state();
    return s.status === 'ready' ? s.dashboard.last30Days.slice(-this.period()) : [];
  });
  protected readonly week = computed(() => {
    const s = this.state();
    return s.status === 'ready' ? s.dashboard.last30Days.slice(-7) : [];
  });
  protected readonly weeklySales = computed(() =>
    this.week().reduce((sum, day) => sum + day.count, 0),
  );
  protected readonly maxDailySales = computed(() =>
    Math.max(...this.week().map((day) => day.count), 1),
  );

  protected dayLabel(date: string): string {
    return new Intl.DateTimeFormat('es-PE', {
      day: 'numeric',
      month: 'short',
      timeZone: 'UTC',
    }).format(new Date(`${date}T12:00:00Z`));
  }

  protected rangeLabel(days: DayAmount[]): string {
    return days.length
      ? `${this.dayLabel(days[0]!.date)} — ${this.dayLabel(days[days.length - 1]!.date)}`
      : 'Sin movimientos en el periodo';
  }

  protected activityLink(item: Dashboard['recentActivity'][number]): string | null {
    switch (item.entityType) {
      case 'ORDER':
        return item.entityId ? '/panel/ventas/' + item.entityId : '/panel/ventas';
      case 'PAYMENT':
        return '/panel/ventas';
      case 'SUBSCRIPTION':
        return '/panel/suscripciones';
      case 'RENEWAL':
      case 'NOTIFICATION':
        return '/panel/renovaciones';
      case 'PRODUCT':
        return item.entityId ? '/panel/productos/' + item.entityId : '/panel/productos';
      case 'CUSTOMER':
        return item.entityId ? '/panel/clientes/' + item.entityId : '/panel/clientes';
      default:
        return null;
    }
  }

  protected storeUrl(slug: string): string {
    return `${window.location.origin}/tienda/${slug}`;
  }

  protected copyStoreUrl(slug: string): void {
    if (this.clipboard.copy(this.storeUrl(slug))) {
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 2500);
    }
  }

  protected setupComplete(s: Setup, b: Business): boolean {
    return s.paymentConfigured && s.publishedProducts > 0 && !!b.logoUrl;
  }

  protected time(iso: string): string {
    const d = new Date(iso);
    const s = this.state();
    const zone = s.status === 'ready' ? s.dashboard.timeZone : 'America/Lima';
    const calendar = new Intl.DateTimeFormat('es-PE', { timeZone: zone });
    const sameDay = calendar.format(d) === calendar.format(new Date());
    return new Intl.DateTimeFormat(
      'es-PE',
      sameDay
        ? { hour: '2-digit', minute: '2-digit', timeZone: zone }
        : { day: 'numeric', month: 'short', timeZone: zone },
    ).format(d);
  }

  protected monthName(): string {
    const s = this.state();
    return new Intl.DateTimeFormat('es-PE', {
      month: 'long',
      timeZone: s.status === 'ready' ? s.dashboard.timeZone : 'America/Lima',
    })
      .format(new Date())
      .toLocaleLowerCase('es-PE');
  }
}
