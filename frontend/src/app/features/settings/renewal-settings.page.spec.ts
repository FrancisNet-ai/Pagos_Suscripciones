import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RenewalSettingsPage } from './renewal-settings.page';

describe('RenewalSettingsPage', () => {
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [RenewalSettingsPage], providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('impide sobrescribir ajustes con valores predeterminados si falla la carga', () => {
    const fixture = TestBed.createComponent(RenewalSettingsPage);
    http.expectOne('/api/v1/renewals/settings').flush({}, { status: 503, statusText: 'Unavailable' });
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('fieldset')!.disabled).toBe(true);
    (el.querySelector('button[type=submit]') as HTMLButtonElement).click();
    http.expectNone((r) => r.method === 'PUT');
    Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.includes('Volver a cargar'))!.click();
    http.expectOne('/api/v1/renewals/settings').flush({ reminderDaysBefore: 7, reminderOnDueDay: false, renewalGraceDays: 5 });
    fixture.detectChanges();
    expect(el.querySelector('fieldset')!.disabled).toBe(false);
  });

  it('guarda la política elegida y confirma solo la respuesta del servidor', () => {
    const fixture = TestBed.createComponent(RenewalSettingsPage);
    http.expectOne('/api/v1/renewals/settings').flush({ reminderDaysBefore: 3, reminderOnDueDay: true, renewalGraceDays: 3 });
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const select = el.querySelector<HTMLSelectElement>('#renewalGraceDays')!;
    select.selectedIndex = 0;
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    (el.querySelector('button[type=submit]') as HTMLButtonElement).click();
    const request = http.expectOne((r) => r.method === 'PUT' && r.url === '/api/v1/renewals/settings');
    expect(request.request.body.renewalGraceDays).toBe(0);
    expect(el.textContent).not.toContain('Cambios guardados');
    request.flush({ reminderDaysBefore: 3, reminderOnDueDay: true, renewalGraceDays: 0 });
    fixture.detectChanges();
    expect(el.textContent).toContain('Cambios guardados');
  });
});
