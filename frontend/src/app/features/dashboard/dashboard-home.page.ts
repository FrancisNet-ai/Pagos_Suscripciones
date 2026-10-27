import { IconComponent } from '../../shared/components/icon.component';
import { Clipboard } from '@angular/cdk/clipboard';
import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, forkJoin, map, of, startWith } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { ApiError } from '../../core/services/api-error';
import { BarChartComponent, BarPoint } from '../../shared/components/bar-chart.component';
import { SolesPipe } from '../../shared/pipes/soles.pipe';
import { Dashboard, ReportsApi } from '../reports/reports.api';
import { Business, SettingsApi } from '../settings/settings.api';

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
  imports: [RouterLink, SolesPipe, BarChartComponent, IconComponent],
  templateUrl: './dashboard-home.page.html',
})
export class DashboardHomePage {
  private readonly http = inject(HttpClient);
  private readonly clipboard = inject(Clipboard);
  protected readonly auth = inject(AuthService);
  protected readonly copied = signal(false);

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

  protected readonly chart = computed<BarPoint[]>(() => {
    const s = this.state();
    if (s.status !== 'ready') return [];
    const days = s.dashboard.last30Days;
    return days.map((d, i) => ({
      label: new Intl.DateTimeFormat('es-PE', { day: 'numeric', month: 'long' }).format(
        new Date(`${d.date}T12:00:00`),
      ),
      tick: d.date.slice(8, 10),
      value: Number(d.amount),
      highlight: i === days.length - 1,
    }));
  });

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
    const sameDay = d.toDateString() === new Date().toDateString();
    return new Intl.DateTimeFormat(
      'es-PE',
      sameDay ? { hour: '2-digit', minute: '2-digit' } : { day: 'numeric', month: 'short' },
    ).format(d);
  }

  protected monthName(): string {
    return new Intl.DateTimeFormat('es-PE', { month: 'long' }).format(new Date());
  }
}
