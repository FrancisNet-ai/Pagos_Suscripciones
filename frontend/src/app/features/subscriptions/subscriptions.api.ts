import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Page } from '../../core/services/page';

export type SubscriptionStatus = 'PENDING_ACTIVATION' | 'ACTIVE' | 'RENEWAL_PENDING' | 'EXPIRED' | 'CANCELLED' | 'ENDED';
export type SubscriptionFilter = 'ALL' | 'PENDING_ACTIVATION' | 'ACTIVE' | 'EXPIRING' | 'RENEWAL' | 'EXPIRED' | 'CLOSED' | 'RELEASE_PENDING';
export type CapacityStatus = 'RESERVED' | 'OCCUPIED' | 'RELEASE_PENDING' | 'RELEASED' | 'EXPIRED';

export interface Subscription {
  id: string;
  customerId: string;
  customerName: string;
  customerPhone: string;
  productId: string;
  productName: string;
  orderId: string | null;
  origin: 'ORDER' | 'MANUAL' | 'IMPORT';
  status: SubscriptionStatus;
  statusLabel: string;
  durationDays: number;
  contractedPrice: number;
  paidAt: string | null;
  startAt: string | null;
  expiresAt: string | null;
  daysRemaining: number | null;
  capacityStatus: CapacityStatus | null;
  cancellationReason: string | null;
  notes: string | null;
  createdAt: string;
}

export interface ManualSubscriptionPayload {
  productId: string;
  startDate: string;
  durationDays: number;
  contractedPrice: number;
  status: 'ACTIVE' | 'EXPIRED' | 'CANCELLED';
  notes: string | null;
}

@Injectable({ providedIn: 'root' })
export class SubscriptionsApi {
  private readonly http = inject(HttpClient);

  list(filter: SubscriptionFilter, query: string, page: number): Observable<Page<Subscription>> {
    const params: Record<string, string> = { filter, page: String(page), size: '20' };
    if (query) params['query'] = query;
    return this.http.get<Page<Subscription>>('/api/v1/subscriptions', { params });
  }

  activate(id: string, startAt: string | null): Observable<Subscription> {
    return this.http.post<Subscription>(`/api/v1/subscriptions/${id}/activate`, startAt ? { startAt } : {});
  }

  cancel(id: string, reason: string): Observable<Subscription> {
    return this.http.post<Subscription>(`/api/v1/subscriptions/${id}/cancel`, { reason });
  }

  releaseCapacity(id: string): Observable<Subscription> {
    return this.http.post<Subscription>(`/api/v1/subscriptions/${id}/release-capacity`, {});
  }
}

export function subscriptionTone(status: SubscriptionStatus): 'success' | 'warning' | 'neutral' | 'danger' | 'info' {
  return ({ ACTIVE: 'success', PENDING_ACTIVATION: 'info', RENEWAL_PENDING: 'warning', EXPIRED: 'danger', CANCELLED: 'neutral', ENDED: 'neutral' } as const)[status];
}

/** Fecha corta en es-PE. */
export function shortDate(iso: string | null): string {
  return iso ? new Intl.DateTimeFormat('es-PE', { day: 'numeric', month: 'short', year: 'numeric' }).format(new Date(iso)) : '—';
}

export function remainingText(s: Pick<Subscription, 'daysRemaining' | 'status'>): string | null {
  if (s.daysRemaining === null) return null;
  if (s.daysRemaining === 0) return 'Vence hoy';
  return s.daysRemaining === 1 ? 'Queda 1 día' : `Quedan ${s.daysRemaining} días`;
}
