import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Page } from '../../core/services/page';
import { OrderSummary } from '../orders/orders.api';

export interface SupportGrant {
  id: string;
  grantedBy: string;
  note: string | null;
  createdAt: string;
  expiresAt: string;
  revokedAt: string | null;
  active: boolean;
}

export interface SupportAccess {
  active: SupportGrant | null;
  grants: SupportGrant[];
  views: { resource: string; reason: string; at: string }[];
}

export interface SupportCustomer {
  id: string;
  fullName: string;
  phone: string;
  activeSubscriptions: number;
}

@Injectable({ providedIn: 'root' })
export class SupportApi {
  private readonly http = inject(HttpClient);

  status(): Observable<SupportAccess> {
    return this.http.get<SupportAccess>('/api/v1/business/me/support-access');
  }

  grant(hours: number, note: string | null): Observable<SupportAccess> {
    return this.http.post<SupportAccess>('/api/v1/business/me/support-access', { hours, note });
  }

  revoke(): Observable<SupportAccess> {
    return this.http.delete<SupportAccess>('/api/v1/business/me/support-access');
  }

  /** Superadministración: solo lectura, con motivo obligatorio en cada consulta. */
  orders(businessId: string, reason: string, query: string): Observable<Page<OrderSummary>> {
    return this.http.get<Page<OrderSummary>>(`/api/v1/admin/businesses/${businessId}/support/orders`, { params: { reason, query } });
  }

  customers(businessId: string, reason: string, query: string): Observable<Page<SupportCustomer>> {
    return this.http.get<Page<SupportCustomer>>(`/api/v1/admin/businesses/${businessId}/support/customers`, { params: { reason, query } });
  }
}
