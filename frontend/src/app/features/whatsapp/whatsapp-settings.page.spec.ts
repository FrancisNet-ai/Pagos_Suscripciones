import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { WhatsAppIntegration } from './whatsapp.api';
import { WhatsAppSettingsPage } from './whatsapp-settings.page';

const saved: WhatsAppIntegration = {
  configured: true, phoneNumberId: '1234567890', wabaId: null, displayPhone: '+51 955 000 111', tokenSaved: true,
  reminderTemplate: 'recordatorio_renovacion', farewellTemplate: null, orderTemplate: null, paymentTemplate: 'pago_verificado', templateLanguage: 'es', enabled: true, status: 'VERIFIED',
  lastVerifiedAt: '2026-10-27T10:00:00Z', lastError: null, planAllows: true, webhooksAvailable: true, webhookUrl: 'https://x/api/v1/public/webhooks/whatsapp',
};

describe('WhatsAppSettingsPage', () => {
  it('nunca muestra el token y al editar sin escribirlo se conserva el guardado', () => {
    TestBed.configureTestingModule({ imports: [WhatsAppSettingsPage], providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(WhatsAppSettingsPage);
    fixture.detectChanges();
    http.expectOne('/api/v1/business/me/whatsapp').flush(saved);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const token = el.querySelector<HTMLInputElement>('#accessToken')!;
    expect(token.value).toBe('');
    expect(token.type).toBe('password');
    expect(el.textContent).toContain('Envío automático activo');

    Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.includes('Guardar y verificar'))!.click();
    const req = http.expectOne({ method: 'PUT', url: '/api/v1/business/me/whatsapp' });
    expect(req.request.body.accessToken).toBeNull();
    expect(req.request.body.reminderTemplate).toBe('recordatorio_renovacion');
    expect(req.request.body.paymentTemplate).toBe('pago_verificado');
    expect(req.request.body.orderTemplate).toBeNull();
    req.flush(saved);
  });

  it('sin la función en el plan el formulario queda deshabilitado', () => {
    TestBed.configureTestingModule({ imports: [WhatsAppSettingsPage], providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(WhatsAppSettingsPage);
    fixture.detectChanges();
    http.expectOne('/api/v1/business/me/whatsapp').flush({ ...saved, configured: false, tokenSaved: false, status: null, enabled: false, planAllows: false });
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Tu plan no incluye avisos automáticos');
    expect(Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.includes('Guardar y verificar'))!.disabled).toBe(true);
  });
});
