import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface WhatsAppIntegration {
  configured: boolean;
  phoneNumberId: string | null;
  wabaId: string | null;
  displayPhone: string | null;
  tokenSaved: boolean;
  reminderTemplate: string | null;
  farewellTemplate: string | null;
  orderTemplate: string | null;
  paymentTemplate: string | null;
  templateLanguage: string;
  enabled: boolean;
  status: 'UNVERIFIED' | 'VERIFIED' | 'ERROR' | null;
  lastVerifiedAt: string | null;
  lastError: string | null;
  planAllows: boolean;
  webhooksAvailable: boolean;
  webhookUrl: string;
}

export interface WhatsAppIntegrationPayload {
  phoneNumberId: string;
  wabaId: string | null;
  accessToken: string | null;
  reminderTemplate: string;
  farewellTemplate: string | null;
  orderTemplate: string | null;
  paymentTemplate: string | null;
  templateLanguage: string;
  enabled: boolean;
}

@Injectable({ providedIn: 'root' })
export class WhatsAppApi {
  private readonly http = inject(HttpClient);
  private readonly url = '/api/v1/business/me/whatsapp';

  get(): Observable<WhatsAppIntegration> {
    return this.http.get<WhatsAppIntegration>(this.url);
  }

  save(body: WhatsAppIntegrationPayload): Observable<WhatsAppIntegration> {
    return this.http.put<WhatsAppIntegration>(this.url, body);
  }

  remove(): Observable<void> {
    return this.http.delete<void>(this.url);
  }
}
