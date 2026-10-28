import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { Page } from '../../core/services/page';

export type OrderStatus = 'AWAITING_PAYMENT' | 'PAYMENT_REPORTED' | 'PAID' | 'CANCELLED' | 'EXPIRED' | 'REFUNDED';
export type OrderFilter = 'ALL' | 'TO_VERIFY' | 'AWAITING_PAYMENT' | 'PAID' | 'CLOSED' | 'REFUNDED';
export type PaymentStatus = 'PENDING' | 'REPORTED' | 'PENDING_VERIFICATION' | 'VERIFIED' | 'REJECTED' | 'CANCELLED' | 'REFUNDED';
export type Method = 'YAPE' | 'PLIN';

export interface OrderSummary {
  id: string;
  orderNumber: string;
  kind: 'PURCHASE' | 'RENEWAL';
  customerName: string;
  customerPhone: string;
  productName: string;
  amount: number;
  method: Method | null;
  paymentStatus: PaymentStatus | null;
  status: OrderStatus;
  statusLabel: string;
  incident: boolean;
  createdAt: string;
}

export interface PaymentDetail {
  id: string;
  method: Method;
  holderName: string;
  receiverPhone: string;
  amount: number;
  status: PaymentStatus;
  transactionReference: string | null;
  hasReceipt: boolean;
  reportedAt: string | null;
  verifiedAt: string | null;
  rejectedAt: string | null;
  rejectionReason: string | null;
  refundedAt: string | null;
  refundReason: string | null;
  refundReference: string | null;
}

export interface OrderDetail {
  id: string;
  orderNumber: string;
  kind: 'PURCHASE' | 'RENEWAL';
  status: OrderStatus;
  statusLabel: string;
  customerId: string;
  customerName: string;
  customerPhone: string;
  customerWhatsappUrl: string;
  amount: number;
  currency: 'PEN';
  items: { productId: string; productName: string; productType: 'SUBSCRIPTION' | 'ONE_TIME'; unitPrice: number; durationDays: number | null; quantity: number }[];
  payments: PaymentDetail[];
  capacityStatus: 'RESERVED' | 'OCCUPIED' | 'RELEASE_PENDING' | 'RELEASED' | 'EXPIRED' | null;
  reservedUntil: string | null;
  incidentReason: string | null;
  cancellationReason: string | null;
  renewalRemindersConsent: boolean;
  subscriptionId: string | null;
  subscriptionStatus: string | null;
  createdAt: string;
  paidAt: string | null;
}

export interface OrderQuery {
  filter: OrderFilter;
  query?: string;
  from?: string;
  to?: string;
  page: number;
}

@Injectable({ providedIn: 'root' })
export class OrdersApi {
  private readonly http = inject(HttpClient);

  list(q: OrderQuery): Observable<Page<OrderSummary>> {
    const params: Record<string, string> = { filter: q.filter, page: String(q.page), size: '20' };
    if (q.query) params['query'] = q.query;
    if (q.from) params['from'] = q.from;
    if (q.to) params['to'] = q.to;
    return this.http.get<Page<OrderSummary>>('/api/v1/orders', { params });
  }

  summary(): Observable<{ toVerify: number }> {
    return this.http.get<{ toVerify: number }>('/api/v1/orders/summary');
  }

  get(id: string): Observable<OrderDetail> {
    return this.http.get<OrderDetail>(`/api/v1/orders/${id}`);
  }

  cancel(id: string, reason: string): Observable<OrderDetail> {
    return this.http.post<OrderDetail>(`/api/v1/orders/${id}/cancel`, { reason });
  }

  verify(paymentId: string): Observable<OrderDetail> {
    return this.http.post<OrderDetail>(`/api/v1/payments/${paymentId}/verify`, {});
  }

  reject(paymentId: string, reason: string): Observable<OrderDetail> {
    return this.http.post<OrderDetail>(`/api/v1/payments/${paymentId}/reject`, { reason });
  }

  /** Registra un reembolso que el titular ya hizo por su cuenta; la plataforma no mueve dinero. */
  refund(paymentId: string, reason: string, refundReference: string | null): Observable<OrderDetail> {
    return this.http.post<OrderDetail>(`/api/v1/payments/${paymentId}/refund`, { reason, refundReference });
  }

  /** El comprobante es privado: se descarga con la sesión y se muestra como URL local. */
  receipt(paymentId: string): Observable<string> {
    return this.http.get(`/api/v1/payments/${paymentId}/receipt`, { responseType: 'blob' }).pipe(map((blob) => URL.createObjectURL(blob)));
  }
}

export const ORDER_FILTERS: { value: OrderFilter; label: string }[] = [
  { value: 'ALL', label: 'Todos' },
  { value: 'TO_VERIFY', label: 'Por verificar' },
  { value: 'AWAITING_PAYMENT', label: 'Pendientes de pago' },
  { value: 'PAID', label: 'Pagados' },
  { value: 'CLOSED', label: 'Cerrados' },
  { value: 'REFUNDED', label: 'Reembolsados' },
];

export function orderTone(status: OrderStatus): 'success' | 'warning' | 'neutral' | 'danger' | 'info' {
  return ({ PAID: 'success', PAYMENT_REPORTED: 'warning', AWAITING_PAYMENT: 'info', CANCELLED: 'neutral', EXPIRED: 'neutral', REFUNDED: 'danger' } as const)[status];
}

export const METHOD_LABEL: Record<Method, string> = { YAPE: 'Yape', PLIN: 'Plin' };

export function formatDateTime(iso: string | null): string {
  return iso ? new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(iso)) : '';
}
