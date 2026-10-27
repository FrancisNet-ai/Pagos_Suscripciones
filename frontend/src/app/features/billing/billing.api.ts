import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { AccessSummary, PlanFeature } from '../../core/auth/auth.models';

export type BillingPeriod = 'MONTHLY' | 'QUARTERLY' | 'YEARLY';
export type InvoiceStatus = 'OPEN' | 'PAID' | 'VOID';
export type SaasPaymentStatus = 'PENDING_VERIFICATION' | 'VERIFIED' | 'REJECTED';
export type Method = 'YAPE' | 'PLIN';

export interface Plan {
  id: string;
  name: string;
  description: string | null;
  price: number;
  billingPeriod: BillingPeriod;
  billingPeriodLabel: string;
  productLimit: number | null;
  customerLimit: number | null;
  adminUserLimit: number;
  featureReports: boolean;
  featureReminders: boolean;
  featureBranding: boolean;
  active: boolean;
  sortOrder: number;
  subscribers?: number | null;
}

export interface SaasPaymentSummary {
  id: string;
  method: Method;
  amount: number;
  transactionReference: string;
  hasReceipt: boolean;
  status: SaasPaymentStatus;
  reportedAt: string;
  verifiedAt: string | null;
  rejectionReason: string | null;
}

export interface Invoice {
  id: string;
  invoiceNumber: string;
  planId: string;
  planName: string;
  billingPeriod: BillingPeriod;
  billingPeriodLabel: string;
  kind: 'NEW' | 'RENEWAL' | 'PLAN_CHANGE';
  kindLabel: string;
  amount: number;
  status: InvoiceStatus;
  periodStart: string | null;
  periodEnd: string | null;
  paidAt: string | null;
  createdAt: string;
  payments: SaasPaymentSummary[];
}

export interface PlatformPaymentOption {
  method: Method;
  methodLabel: string;
  holderName: string;
  phone: string;
  qrImageUrl: string | null;
}

export interface BillingOverview {
  access: AccessSummary;
  suspensionReason: string | null;
  currentPlan: Plan | null;
  startAt: string | null;
  graceDays: number;
  products: { used: number; limit: number | null };
  customers: { used: number; limit: number | null };
  openInvoice: Invoice | null;
  paymentOptions: PlatformPaymentOption[];
  support: { whatsapp: string | null; whatsappUrl: string | null; email: string | null };
  plans: Plan[];
  history: Invoice[];
}

@Injectable({ providedIn: 'root' })
export class BillingApi {
  private readonly http = inject(HttpClient);

  publicPlans(): Observable<Plan[]> {
    return this.http.get<Plan[]>('/api/v1/public/saas-plans');
  }

  overview(): Observable<BillingOverview> {
    return this.http.get<BillingOverview>('/api/v1/billing');
  }

  checkout(planId: string): Observable<BillingOverview> {
    return this.http.post<BillingOverview>('/api/v1/billing/checkout', { planId });
  }

  cancel(invoiceId: string): Observable<BillingOverview> {
    return this.http.post<BillingOverview>(`/api/v1/billing/invoices/${invoiceId}/cancel`, {});
  }

  reportPayment(invoiceId: string, method: Method, transactionReference: string, receipt: File | null): Observable<BillingOverview> {
    const body = new FormData();
    body.append('method', method);
    body.append('transactionReference', transactionReference);
    if (receipt) body.append('receipt', receipt);
    return this.http.post<BillingOverview>(`/api/v1/billing/invoices/${invoiceId}/payments`, body);
  }
}

export const FEATURE_LABEL: Record<PlanFeature, string> = {
  REPORTS: 'Reportes de ventas',
  REMINDERS: 'Avisos de renovación automáticos',
  BRANDING: 'Colores y portada propios en tu tienda',
};

/**
 * Líneas legibles de lo que incluye un plan. El límite de administradores no se muestra: las cuentas de
 * colaboradores aún no existen y no se anuncia algo que no funciona.
 */
export function planLines(p: Plan): string[] {
  const n = new Intl.NumberFormat('es-PE');
  return [
    p.productLimit ? (p.productLimit === 1 ? '1 producto activo' : `Hasta ${n.format(p.productLimit)} productos activos`) : 'Productos ilimitados',
    p.customerLimit ? (p.customerLimit === 1 ? '1 cliente' : `Hasta ${n.format(p.customerLimit)} clientes`) : 'Clientes ilimitados',
    p.adminUserLimit === 1 ? 'Solo el titular' : `Hasta ${n.format(p.adminUserLimit)} usuarios (titular incluido)`,
    ...(p.featureReports ? [FEATURE_LABEL.REPORTS] : []),
    ...(p.featureReminders ? [FEATURE_LABEL.REMINDERS] : []),
    ...(p.featureBranding ? [FEATURE_LABEL.BRANDING] : []),
  ];
}

export function periodSuffix(period: BillingPeriod): string {
  return { MONTHLY: 'al mes', QUARTERLY: 'cada 3 meses', YEARLY: 'al año' }[period];
}

export function formatDate(iso: string | null): string {
  return iso ? new Intl.DateTimeFormat('es-PE', { dateStyle: 'long' }).format(new Date(iso)) : '';
}
