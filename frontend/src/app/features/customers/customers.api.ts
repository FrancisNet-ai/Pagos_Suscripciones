import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Page } from '../../core/services/page';
import { OrderSummary } from '../orders/orders.api';
import { ManualSubscriptionPayload, Subscription } from '../subscriptions/subscriptions.api';

export type CustomerFilter = 'ALL' | 'ACTIVE' | 'EXPIRING' | 'EXPIRED';

export interface CustomerSummary {
  id: string;
  fullName: string;
  phone: string;
  source: 'STOREFRONT' | 'MANUAL' | 'IMPORT';
  activeSubscriptions: number;
  nextExpiry: string | null;
  hasExpired: boolean;
  createdAt: string;
}

export interface CustomerDetail {
  id: string;
  fullName: string;
  phone: string;
  source: 'STOREFRONT' | 'MANUAL' | 'IMPORT';
  notes: string | null;
  createdAt: string;
  renewalRemindersConsent: boolean;
  paidOrders: number;
  subscriptions: Subscription[];
  orders: OrderSummary[];
}

@Injectable({ providedIn: 'root' })
export class CustomersApi {
  private readonly http = inject(HttpClient);

  list(filter: CustomerFilter, query: string, page: number): Observable<Page<CustomerSummary>> {
    const params: Record<string, string> = { filter, page: String(page), size: '20' };
    if (query) params['query'] = query;
    return this.http.get<Page<CustomerSummary>>('/api/v1/customers', { params });
  }

  get(id: string): Observable<CustomerDetail> {
    return this.http.get<CustomerDetail>(`/api/v1/customers/${id}`);
  }

  create(payload: { fullName: string; phone: string; notes: string | null; subscription: ManualSubscriptionPayload | null }): Observable<CustomerDetail> {
    return this.http.post<CustomerDetail>('/api/v1/customers', payload);
  }

  update(id: string, payload: { fullName: string; notes: string | null }): Observable<CustomerDetail> {
    return this.http.put<CustomerDetail>(`/api/v1/customers/${id}`, payload);
  }

  addSubscription(id: string, payload: ManualSubscriptionPayload): Observable<CustomerDetail> {
    return this.http.post<CustomerDetail>(`/api/v1/customers/${id}/subscriptions`, payload);
  }
}
