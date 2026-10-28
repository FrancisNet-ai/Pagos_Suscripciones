import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { AccessSummary, SaasSubscriptionStatus } from '../../core/auth/auth.models';
import { Page } from '../../core/services/page';
import { BillingPeriod, Invoice, Method, Plan, SaasPaymentStatus } from '../billing/billing.api';

export type BusinessFilter = 'ALL' | 'ACTIVE' | 'NO_PLAN' | 'PENDING_PAYMENT' | 'PAST_DUE' | 'EXPIRED' | 'SUSPENDED';

export interface AdminOverview {
  businessesTotal: number;
  businessesActive: number;
  businessesNoPlan: number;
  subscriptionsPendingPayment: number;
  subscriptionsPastDue: number;
  subscriptionsExpired: number;
  businessesSuspended: number;
  paymentsToVerify: number;
  revenueMonth: number;
  revenueTotal: number;
  newBusinesses30Days: number;
  plans: { planId: string; planName: string; businesses: number }[];
  last12Months: { month: string; label: string; amount: number }[];
}

export interface AdminBusinessSummary {
  id: string;
  businessName: string;
  slug: string;
  uniqueCode: string;
  ownerName: string;
  ownerEmail: string;
  status: 'ACTIVE' | 'SUSPENDED';
  planName: string | null;
  subscriptionStatus: SaasSubscriptionStatus | null;
  expiresAt: string | null;
  createdAt: string;
}

export interface AdminAuditEntry {
  id: string;
  action: string;
  actorEmail: string | null;
  businessId: string | null;
  businessName: string | null;
  entityType: string | null;
  metadata: string | null;
  ipAddress: string | null;
  createdAt: string;
}

export interface AdminBusinessDetail {
  summary: AdminBusinessSummary;
  ownerPhone: string;
  suspensionReason: string | null;
  suspendedAt: string | null;
  access: AccessSummary;
  usage: { products: number; customers: number; ordersPaid: number };
  invoices: Invoice[];
  activity: AdminAuditEntry[];
  /** Autorización vigente del titular para consultas de soporte. */
  supportAccessUntil?: string | null;
}

export interface AdminSaasPayment {
  id: string;
  businessId: string;
  businessName: string;
  invoiceId: string;
  invoiceNumber: string;
  planName: string;
  method: Method;
  receiverHolder: string;
  receiverPhone: string;
  amount: number;
  transactionReference: string;
  hasReceipt: boolean;
  status: SaasPaymentStatus;
  reportedAt: string;
  verifiedAt: string | null;
  rejectionReason: string | null;
}

export interface PlanPayload {
  name: string;
  description: string | null;
  price: number;
  billingPeriod: BillingPeriod;
  productLimit: number | null;
  customerLimit: number | null;
  adminUserLimit: number;
  featureReports: boolean;
  featureReminders: boolean;
  featureBranding: boolean;
  active: boolean;
  sortOrder: number;
}

export interface PlatformPaymentMethod {
  method: Method;
  methodLabel: string;
  holderName: string | null;
  phone: string | null;
  qrImageUrl: string | null;
  enabled: boolean;
}

export interface PlatformSettings {
  supportWhatsapp: string | null;
  supportEmail: string | null;
  saasGraceDays: number;
  paymentMethods: PlatformPaymentMethod[];
}

@Injectable({ providedIn: 'root' })
export class AdminApi {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/admin';

  overview(): Observable<AdminOverview> {
    return this.http.get<AdminOverview>(`${this.base}/overview`);
  }

  businesses(q: string, filter: BusinessFilter, page: number): Observable<Page<AdminBusinessSummary>> {
    const params = new HttpParams().set('q', q).set('filter', filter).set('page', page).set('size', 20);
    return this.http.get<Page<AdminBusinessSummary>>(`${this.base}/businesses`, { params });
  }

  business(id: string): Observable<AdminBusinessDetail> {
    return this.http.get<AdminBusinessDetail>(`${this.base}/businesses/${id}`);
  }

  suspend(id: string, reason: string): Observable<AdminBusinessDetail> {
    return this.http.post<AdminBusinessDetail>(`${this.base}/businesses/${id}/suspend`, { reason });
  }

  reactivate(id: string): Observable<AdminBusinessDetail> {
    return this.http.post<AdminBusinessDetail>(`${this.base}/businesses/${id}/reactivate`, {});
  }

  payments(status: SaasPaymentStatus | null, page: number): Observable<Page<AdminSaasPayment>> {
    let params = new HttpParams().set('page', page).set('size', 20);
    if (status) params = params.set('status', status);
    return this.http.get<Page<AdminSaasPayment>>(`${this.base}/saas-payments`, { params });
  }

  verify(id: string): Observable<void> {
    return this.http.post<void>(`${this.base}/saas-payments/${id}/verify`, {});
  }

  reject(id: string, reason: string): Observable<void> {
    return this.http.post<void>(`${this.base}/saas-payments/${id}/reject`, { reason });
  }

  /** Comprobante privado: se descarga con la sesión y se muestra como URL local. */
  receipt(id: string): Observable<string> {
    return this.http.get(`${this.base}/saas-payments/${id}/receipt`, { responseType: 'blob' }).pipe(map((b) => URL.createObjectURL(b)));
  }

  plans(): Observable<Plan[]> {
    return this.http.get<Plan[]>(`${this.base}/plans`);
  }

  createPlan(body: PlanPayload): Observable<Plan> {
    return this.http.post<Plan>(`${this.base}/plans`, body);
  }

  updatePlan(id: string, body: PlanPayload): Observable<Plan> {
    return this.http.put<Plan>(`${this.base}/plans/${id}`, body);
  }

  auditLogs(action: string, page: number): Observable<Page<AdminAuditEntry>> {
    let params = new HttpParams().set('page', page).set('size', 30);
    if (action) params = params.set('action', action);
    return this.http.get<Page<AdminAuditEntry>>(`${this.base}/audit-logs`, { params });
  }

  settings(): Observable<PlatformSettings> {
    return this.http.get<PlatformSettings>(`${this.base}/settings`);
  }

  updateSettings(body: { supportWhatsapp: string | null; supportEmail: string | null; saasGraceDays: number }): Observable<PlatformSettings> {
    return this.http.put<PlatformSettings>(`${this.base}/settings`, body);
  }

  updatePaymentMethod(method: Method, body: { holderName: string | null; phone: string | null; enabled: boolean }): Observable<PlatformSettings> {
    return this.http.put<PlatformSettings>(`${this.base}/settings/payment-methods/${method.toLowerCase()}`, body);
  }

  uploadQr(method: Method, file: File): Observable<PlatformSettings> {
    const body = new FormData();
    body.append('file', file);
    return this.http.post<PlatformSettings>(`${this.base}/settings/payment-methods/${method.toLowerCase()}/qr`, body);
  }

  deleteQr(method: Method): Observable<PlatformSettings> {
    return this.http.delete<PlatformSettings>(`${this.base}/settings/payment-methods/${method.toLowerCase()}/qr`);
  }
}

export const SUBSCRIPTION_LABEL: Record<SaasSubscriptionStatus, string> = {
  PENDING_PAYMENT: 'Pago pendiente',
  ACTIVE: 'Vigente',
  PAST_DUE: 'En gracia',
  CANCELLED: 'Sin plan',
  EXPIRED: 'Vencido',
};

export function subscriptionTone(s: SaasSubscriptionStatus | null): 'success' | 'warning' | 'neutral' | 'danger' | 'info' {
  if (!s) return 'neutral';
  return ({ PENDING_PAYMENT: 'info', ACTIVE: 'success', PAST_DUE: 'warning', CANCELLED: 'neutral', EXPIRED: 'danger' } as const)[s];
}

/** Etiquetas de auditoría legibles; las acciones desconocidas se muestran tal cual. */
export const ACTION_LABEL: Record<string, string> = {
  USER_REGISTERED: 'Registro de vendedor',
  LOGIN_SUCCEEDED: 'Inicio de sesión',
  LOGIN_FAILED: 'Inicio de sesión fallido',
  LOGOUT: 'Cierre de sesión',
  SAAS_PLAN_CREATED: 'Plan creado',
  SAAS_PLAN_UPDATED: 'Plan editado',
  SAAS_CHECKOUT_STARTED: 'Cobro SaaS generado',
  SAAS_INVOICE_CANCELLED: 'Cobro SaaS anulado',
  SAAS_PAYMENT_REPORTED: 'Pago SaaS reportado',
  SAAS_PAYMENT_VERIFIED: 'Pago SaaS verificado',
  SAAS_PAYMENT_REJECTED: 'Pago SaaS rechazado',
  SAAS_RECEIPT_VIEWED: 'Comprobante SaaS consultado',
  SAAS_SUBSCRIPTION_ACTIVATED: 'Plan activado o extendido',
  SAAS_SUBSCRIPTION_PAST_DUE: 'Plan vencido (en gracia)',
  SAAS_SUBSCRIPTION_EXPIRED: 'Plan sin vigencia',
  BUSINESS_SUSPENDED: 'Negocio suspendido',
  BUSINESS_REACTIVATED: 'Negocio reactivado',
  PLATFORM_SETTINGS_UPDATED: 'Configuración general editada',
  BUSINESS_SLUG_CHANGED: 'Enlace público cambiado',
};

export function actionLabel(action: string): string {
  return ACTION_LABEL[action] ?? action.toLowerCase().replaceAll('_', ' ');
}
