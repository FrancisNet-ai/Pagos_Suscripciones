import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type PaymentMethodType = 'YAPE' | 'PLIN';
export type ProductType = 'SUBSCRIPTION' | 'ONE_TIME';

export interface PublicStore {
  slug: string;
  businessName: string;
  description: string | null;
  logoUrl: string | null;
  coverUrl: string | null;
  primaryColor: string | null;
  acceptingOrders: boolean;
  notice: string | null;
  paymentMethods: { method: PaymentMethodType; label: string }[];
  /** La tienda envía confirmaciones del pedido por WhatsApp a quien lo autorice. */
  orderUpdatesByWhatsapp: boolean;
}

export interface PublicProduct {
  id: string;
  name: string;
  description: string | null;
  category: string | null;
  productType: ProductType;
  price: number;
  currency: 'PEN';
  durationDays: number | null;
  imageUrl: string | null;
  availableSlots: number | null;
  soldOut: boolean;
  purchasable: boolean;
}

export interface CreateOrderPayload {
  productId: string;
  customerName: string;
  phone: string;
  paymentMethod: PaymentMethodType;
  acceptTerms: boolean;
  acceptDataProcessing: boolean;
  acceptRenewalReminders: boolean;
  acceptOrderUpdates: boolean;
}

export interface PaymentInstructions {
  method: PaymentMethodType;
  methodLabel: string;
  holderName: string;
  phone: string;
  qrImageUrl: string | null;
  amount: number;
  reference: string;
  automaticVerification: boolean;
}

export type OrderStatus = 'AWAITING_PAYMENT' | 'PAYMENT_REPORTED' | 'PAID' | 'CANCELLED' | 'EXPIRED';

export interface PublicOrder {
  orderNumber: string;
  status: OrderStatus;
  statusLabel: string;
  createdAt: string;
  store: { slug: string; businessName: string; logoUrl: string | null; primaryColor: string | null };
  customerName: string;
  customerPhoneMasked: string;
  productName: string;
  productType: ProductType;
  durationDays: number | null;
  amount: number;
  currency: 'PEN';
  payment: {
    status: string;
    instructions: PaymentInstructions;
    transactionReference: string | null;
    reportedAt: string | null;
    rejectionReason: string | null;
  } | null;
  reservedUntil: string | null;
  canReportPayment: boolean;
  whatsappUrl: string | null;
}

const STORES = '/api/v1/public/stores';
const ORDERS = '/api/v1/public/orders';

@Injectable({ providedIn: 'root' })
export class StorefrontApi {
  private readonly http = inject(HttpClient);

  store(slug: string): Observable<PublicStore> {
    return this.http.get<PublicStore>(`${STORES}/${encodeURIComponent(slug)}`);
  }

  products(slug: string): Observable<PublicProduct[]> {
    return this.http.get<PublicProduct[]>(`${STORES}/${encodeURIComponent(slug)}/products`);
  }

  product(slug: string, productId: string): Observable<PublicProduct> {
    return this.http.get<PublicProduct>(`${STORES}/${encodeURIComponent(slug)}/products/${encodeURIComponent(productId)}`);
  }

  /** La clave de idempotencia evita pedidos duplicados si el comprador reintenta con mala conexión. */
  createOrder(slug: string, payload: CreateOrderPayload, idempotencyKey: string): Observable<{ publicToken: string; orderNumber: string }> {
    return this.http.post<{ publicToken: string; orderNumber: string }>(`${STORES}/${encodeURIComponent(slug)}/orders`, payload, {
      headers: { 'Idempotency-Key': idempotencyKey },
    });
  }

  order(token: string): Observable<PublicOrder> {
    return this.http.get<PublicOrder>(`${ORDERS}/${encodeURIComponent(token)}`);
  }

  reportPayment(token: string, transactionReference: string, receipt: File | null): Observable<PublicOrder> {
    const data = new FormData();
    data.append('transactionReference', transactionReference);
    if (receipt) data.append('receipt', receipt);
    return this.http.post<PublicOrder>(`${ORDERS}/${encodeURIComponent(token)}/payment-report`, data);
  }
}
