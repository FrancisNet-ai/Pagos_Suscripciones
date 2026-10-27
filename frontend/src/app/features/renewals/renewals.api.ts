import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Page } from '../../core/services/page';

export type RenewalStatus = 'OPEN' | 'AWAITING_PAYMENT' | 'COMPLETED' | 'DECLINED' | 'EXPIRED';
export type RenewalResponse = 'NONE' | 'ACCEPTED' | 'DECLINED';
export type RenewalBucket = 'UPCOMING' | 'ACCEPTED' | 'DECLINED' | 'NO_RESPONSE' | 'COMPLETED';

export interface PublicRenewal {
  status: RenewalStatus;
  response: RenewalResponse;
  store: { slug: string; businessName: string; logoUrl: string | null; primaryColor: string | null };
  customerFirstName: string;
  productName: string;
  durationDays: number | null;
  price: number;
  currentExpiresAt: string;
  canRespond: boolean;
  canPay: boolean;
  paymentMethods: { method: 'YAPE' | 'PLIN'; label: string }[];
  farewellMessage: string | null;
  optedOut: boolean;
}

export interface RenewalSummary {
  id: string;
  customerId: string;
  customerName: string;
  customerPhone: string;
  subscriptionId: string;
  subscriptionStatus: string;
  productName: string;
  cycleExpiresAt: string;
  status: RenewalStatus;
  response: RenewalResponse;
  respondedAt: string | null;
  orderId: string | null;
  renewalUrl: string;
}

export interface NotificationItem {
  id: string;
  type: 'REMINDER_BEFORE' | 'REMINDER_DUE_DAY' | 'FAREWELL';
  typeLabel: string;
  customerId: string;
  customerName: string;
  customerPhone: string;
  productName: string;
  scheduledAt: string;
  message: string;
  whatsappUrl: string;
  consent: boolean;
}

export interface RenewalsOverview {
  notificationsDue: number;
  upcoming: number;
  accepted: number;
  declined: number;
  noResponse: number;
  completed: number;
  capacityReleasePending: number;
}

export interface RenewalSettings {
  reminderDaysBefore: number;
  reminderOnDueDay: boolean;
  renewalGraceDays: number;
}

@Injectable({ providedIn: 'root' })
export class RenewalsApi {
  private readonly http = inject(HttpClient);

  // Comprador
  view(token: string): Observable<PublicRenewal> {
    return this.http.get<PublicRenewal>(`/api/v1/public/renewals/${encodeURIComponent(token)}`);
  }

  respond(token: string, response: 'ACCEPTED' | 'DECLINED'): Observable<PublicRenewal> {
    return this.http.post<PublicRenewal>(`/api/v1/public/renewals/${encodeURIComponent(token)}/response`, { response });
  }

  createOrder(token: string, paymentMethod: 'YAPE' | 'PLIN'): Observable<{ publicToken: string; orderNumber: string }> {
    return this.http.post<{ publicToken: string; orderNumber: string }>(`/api/v1/public/renewals/${encodeURIComponent(token)}/orders`, { paymentMethod });
  }

  optOut(token: string): Observable<PublicRenewal> {
    return this.http.post<PublicRenewal>(`/api/v1/public/renewals/${encodeURIComponent(token)}/opt-out`, {});
  }

  // Vendedor
  overview(): Observable<RenewalsOverview> {
    return this.http.get<RenewalsOverview>('/api/v1/renewals/overview');
  }

  list(bucket: RenewalBucket, page: number): Observable<Page<RenewalSummary>> {
    return this.http.get<Page<RenewalSummary>>('/api/v1/renewals', { params: { bucket, page: String(page), size: '20' } });
  }

  notifications(): Observable<Page<NotificationItem>> {
    return this.http.get<Page<NotificationItem>>('/api/v1/renewals/notifications');
  }

  markSent(id: string): Observable<NotificationItem> {
    return this.http.post<NotificationItem>(`/api/v1/renewals/notifications/${id}/sent`, {});
  }

  skip(id: string): Observable<void> {
    return this.http.post<void>(`/api/v1/renewals/notifications/${id}/skip`, {});
  }

  settings(): Observable<RenewalSettings> {
    return this.http.get<RenewalSettings>('/api/v1/renewals/settings');
  }

  updateSettings(body: RenewalSettings): Observable<RenewalSettings> {
    return this.http.put<RenewalSettings>('/api/v1/renewals/settings', body);
  }
}
