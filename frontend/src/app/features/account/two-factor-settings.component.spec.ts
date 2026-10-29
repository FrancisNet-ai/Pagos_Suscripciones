import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TwoFactorSettingsComponent } from './two-factor-settings.component';

function type(el: HTMLElement, id: string, value: string): void {
  const input = el.querySelector<HTMLInputElement>(`#${id}`)!;
  input.value = value;
  input.dispatchEvent(new Event('input'));
}

function click(el: HTMLElement, text: string): void {
  Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.includes(text))!.click();
}

describe('TwoFactorSettingsComponent', () => {
  it('activa con contraseña, QR y código, y muestra los códigos de recuperación', () => {
    TestBed.configureTestingModule({ imports: [TwoFactorSettingsComponent], providers: [provideHttpClient(), provideHttpClientTesting()] });
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(TwoFactorSettingsComponent);
    const el = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
    http.expectOne('/api/v1/auth/2fa').flush({ enabled: false, enabledAt: null, recoveryCodesLeft: 0, required: true });
    fixture.detectChanges();
    expect(el.textContent).toContain('debe activarla');

    click(el, 'Activar verificación en dos pasos');
    fixture.detectChanges();
    type(el, 'tfaPassword', 'clave-segura-123');
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    http.expectOne({ method: 'POST', url: '/api/v1/auth/2fa/setup' })
      .flush({ secret: 'ABCD EFGH', otpauthUri: 'otpauth://totp/x', qrDataUrl: 'data:image/svg+xml;base64,AA==' });
    fixture.detectChanges();
    expect(el.querySelector('img')?.getAttribute('src')).toContain('data:image/svg+xml');
    expect(el.textContent).toContain('ABCD EFGH');

    type(el, 'tfaCode', '123456');
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    const enable = http.expectOne({ method: 'POST', url: '/api/v1/auth/2fa/enable' });
    expect(enable.request.body).toEqual({ code: '123456' });
    enable.flush({ recoveryCodes: ['AAAAA-BBBBB', 'CCCCC-DDDDD'], otherSessionsClosed: 1 });
    fixture.detectChanges();
    expect(el.textContent).toContain('AAAAA-BBBBB');
    expect(el.textContent).toContain('Cerramos 1 sesión en otros dispositivos.');
  });

  it('no permite desactivarla cuando es obligatoria', () => {
    TestBed.configureTestingModule({ imports: [TwoFactorSettingsComponent], providers: [provideHttpClient(), provideHttpClientTesting()] });
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(TwoFactorSettingsComponent);
    const el = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
    http.expectOne('/api/v1/auth/2fa').flush({ enabled: true, enabledAt: '2026-10-29T10:00:00Z', recoveryCodesLeft: 9, required: true });
    fixture.detectChanges();
    expect(el.textContent).toContain('Te quedan 9 códigos');
    expect(el.textContent).not.toContain('Desactivar');
  });
});
