import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';

export interface Business {
  id: string;
  uniqueCode: string;
  slug: string;
  businessName: string;
  description: string | null;
  logoUrl: string | null;
  coverUrl: string | null;
  primaryColor: string | null;
  whatsappNumber: string | null;
  timeZone: string;
  status: 'ACTIVE' | 'SUSPENDED';
  createdAt: string;
}

export interface UpdateBusinessPayload {
  businessName: string;
  description: string | null;
  primaryColor: string | null;
  whatsappNumber: string | null;
  timeZone: string;
}

export interface SlugAvailability {
  slug: string;
  available: boolean;
  reason: string | null;
}

export type PaymentMethodType = 'YAPE' | 'PLIN';

export interface PaymentMethod {
  method: PaymentMethodType;
  label: string;
  configured: boolean;
  holderName: string | null;
  phone: string | null;
  qrImageUrl: string | null;
  enabled: boolean;
}

const BUSINESS = '/api/v1/business/me';

/** Cliente HTTP de la configuración del negocio. Mantiene sincronizado el resumen de sesión. */
@Injectable({ providedIn: 'root' })
export class SettingsApi {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  getBusiness(): Observable<Business> {
    return this.http.get<Business>(BUSINESS);
  }

  updateBusiness(payload: UpdateBusinessPayload): Observable<Business> {
    return this.http.put<Business>(BUSINESS, payload).pipe(tap((b) => this.sync(b)));
  }

  checkSlug(slug: string): Observable<SlugAvailability> {
    return this.http.get<SlugAvailability>(`${BUSINESS}/slug-availability`, { params: { slug } });
  }

  changeSlug(slug: string): Observable<Business> {
    return this.http.put<Business>(`${BUSINESS}/slug`, { slug }).pipe(tap((b) => this.sync(b)));
  }

  uploadImage(kind: 'logo' | 'cover', file: File): Observable<Business> {
    return this.http.post<Business>(`${BUSINESS}/${kind}`, toFormData(file));
  }

  deleteImage(kind: 'logo' | 'cover'): Observable<Business> {
    return this.http.delete<Business>(`${BUSINESS}/${kind}`);
  }

  paymentMethods(): Observable<PaymentMethod[]> {
    return this.http.get<PaymentMethod[]>(`${BUSINESS}/payment-methods`);
  }

  updatePaymentMethod(method: PaymentMethodType, payload: { holderName: string; phone: string; enabled: boolean }): Observable<PaymentMethod> {
    return this.http.put<PaymentMethod>(`${BUSINESS}/payment-methods/${method}`, payload);
  }

  uploadQr(method: PaymentMethodType, file: File): Observable<PaymentMethod> {
    return this.http.post<PaymentMethod>(`${BUSINESS}/payment-methods/${method}/qr`, toFormData(file));
  }

  deleteQr(method: PaymentMethodType): Observable<PaymentMethod> {
    return this.http.delete<PaymentMethod>(`${BUSINESS}/payment-methods/${method}/qr`);
  }

  private sync(business: Business): void {
    this.auth.updateBusinessSummary({ businessName: business.businessName, slug: business.slug, status: business.status });
  }
}

export function toFormData(file: File): FormData {
  const data = new FormData();
  data.append('file', file);
  return data;
}
