import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TelegramConnectComponent } from './telegram-connect.component';

describe('TelegramConnectComponent', () => {
  function setup() {
    TestBed.configureTestingModule({ imports: [TelegramConnectComponent], providers: [provideHttpClient(), provideHttpClientTesting()] });
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(TelegramConnectComponent);
    fixture.detectChanges();
    return { http, fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('no se muestra si la plataforma no tiene el bot', () => {
    const { http, fixture, el } = setup();
    http.expectOne('/api/v1/telegram').flush({ available: false, linked: false, botUsername: null });
    fixture.detectChanges();
    expect(el.querySelector('section')).toBeNull();
  });

  it('muestra conectar y, ya conectado, permite desconectar', () => {
    const { http, fixture, el } = setup();
    http.expectOne('/api/v1/telegram').flush({ available: true, linked: false, botUsername: 'NuvyraAvisosBot' });
    fixture.detectChanges();
    expect(el.textContent).toContain('@NuvyraAvisosBot');
    expect(Array.from(el.querySelectorAll('button')).some((b) => b.textContent?.includes('Conectar Telegram'))).toBe(true);

    Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.includes('Ya lo conecté'))?.click();
    // Recargar el estado tras conectar.
    (fixture.componentInstance as unknown as { refresh(): void }).refresh();
    http.expectOne('/api/v1/telegram').flush({ available: true, linked: true, botUsername: 'NuvyraAvisosBot' });
    fixture.detectChanges();
    expect(el.textContent).toContain('Conectado');
    Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.includes('Desconectar'))!.click();
    http.expectOne({ method: 'DELETE', url: '/api/v1/telegram/link' }).flush(null);
    http.expectOne('/api/v1/telegram').flush({ available: true, linked: false, botUsername: 'NuvyraAvisosBot' });
    fixture.detectChanges();
    expect(el.textContent).toContain('Conectar Telegram');
  });
});
