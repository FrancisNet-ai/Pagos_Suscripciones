import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface Money {
  amount: number;
  count: number;
}

export interface DayAmount {
  date: string;
  amount: number;
  count: number;
}

export interface Dashboard {
  timeZone: string;
  confirmedToday: Money;
  confirmedMonth: Money;
  pendingVerification: Money;
  renewalsMonth: Money;
  notificationsDue: number;
  customers: number;
  subscriptions: { pendingActivation: number; active: number; expiringSoon: number; expired: number; capacityReleasePending: number };
  soldOutProducts: { id: string; name: string; capacity: number | null }[];
  last30Days: DayAmount[];
  recentActivity: { at: string; text: string; entityType: string | null; entityId: string | null }[];
}

export interface SalesReport {
  from: string;
  to: string;
  confirmed: Money;
  pending: Money;
  byDay: DayAmount[];
  byProduct: { productId: string; productName: string; amount: number; count: number }[];
  ordersByStatus: { status: string; count: number; amount: number }[];
  recurringCustomers: number;
  topRecurring: { customerId: string; fullName: string; phone: string; paidOrders: number; amount: number }[];
  manualSubscriptionsRegistered: number;
  renewals: { confirmed: Money; accepted: number; declined: number; noResponse: number; pending: number };
}

@Injectable({ providedIn: 'root' })
export class ReportsApi {
  private readonly http = inject(HttpClient);

  dashboard(): Observable<Dashboard> {
    return this.http.get<Dashboard>('/api/v1/reports/dashboard');
  }

  sales(from: string, to: string): Observable<SalesReport> {
    return this.http.get<SalesReport>('/api/v1/reports/sales', { params: { from, to } });
  }
}
