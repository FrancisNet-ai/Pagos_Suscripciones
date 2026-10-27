import { Clipboard } from '@angular/cdk/clipboard';
import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, of, startWith } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { ApiError } from '../../core/services/api-error';

interface BusinessDetail {
  id: string;
  uniqueCode: string;
  slug: string;
  businessName: string;
  whatsappNumber: string | null;
  timeZone: string;
  status: 'ACTIVE' | 'SUSPENDED';
  createdAt: string;
}

type LoadState = { status: 'loading' } | { status: 'error'; message: string } | { status: 'ready'; business: BusinessDetail };

@Component({
  selector: 'app-dashboard-home-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dashboard-home.page.html',
})
export class DashboardHomePage {
  private readonly http = inject(HttpClient);
  private readonly clipboard = inject(Clipboard);
  protected readonly auth = inject(AuthService);
  protected readonly copied = signal(false);

  protected readonly state = toSignal(
    this.http.get<BusinessDetail>('/api/v1/business/me').pipe(
      map((business): LoadState => ({ status: 'ready', business })),
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
