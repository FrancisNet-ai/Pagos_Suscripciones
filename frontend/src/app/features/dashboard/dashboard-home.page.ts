import { Clipboard } from '@angular/cdk/clipboard';
import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, forkJoin, map, of, startWith } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { ApiError } from '../../core/services/api-error';
import { Business } from '../settings/settings.api';

interface Setup {
  paymentConfigured: boolean;
  publishedProducts: number;
}

type LoadState =
  | { status: 'loading' }
  | { status: 'error'; message: string }
  | { status: 'ready'; business: Business; setup: Setup };

@Component({
  selector: 'app-dashboard-home-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  templateUrl: './dashboard-home.page.html',
})
export class DashboardHomePage {
  private readonly http = inject(HttpClient);
  private readonly clipboard = inject(Clipboard);
  protected readonly auth = inject(AuthService);
  protected readonly copied = signal(false);

  protected readonly state = toSignal(
    forkJoin({
      business: this.http.get<Business>('/api/v1/business/me'),
      methods: this.http.get<{ enabled: boolean }[]>('/api/v1/business/me/payment-methods'),
      published: this.http.get<{ totalItems: number }>('/api/v1/products', { params: { filter: 'PUBLISHED', size: 1 } }),
    }).pipe(
      map(
        ({ business, methods, published }): LoadState => ({
          status: 'ready',
          business,
          setup: { paymentConfigured: methods.some((m) => m.enabled), publishedProducts: published.totalItems },
        }),
      ),
      catchError((e: unknown) => of<LoadState>({ status: 'error', message: ApiError.from(e).message })),
      startWith<LoadState>({ status: 'loading' }),
    ),
    { requireSync: true },
  );

  protected readonly firstName = computed(() => this.auth.user()?.fullName.split(' ')[0] ?? '');

  protected storeUrl(slug: string): string {
    return `${window.location.origin}/tienda/${slug}`;
  }

  protected copyStoreUrl(slug: string): void {
    if (this.clipboard.copy(this.storeUrl(slug))) {
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 2500);
    }
  }

  /** Fecha en la zona horaria del negocio (por defecto America/Lima). */
  protected formatDate(iso: string, timeZone: string): string {
    return new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium', timeStyle: 'short', timeZone }).format(new Date(iso));
  }

  protected formatPhone(phone: string | null): string {
    if (!phone) return 'Sin configurar';
    const local = phone.replace(/^\+51/, '');
    return `+51 ${local.slice(0, 3)} ${local.slice(3, 6)} ${local.slice(6)}`;
  }
}
