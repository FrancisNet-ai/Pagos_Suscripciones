import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SessionsListComponent } from './sessions-list.component';

describe('SessionsListComponent', () => {
  it('muestra las sesiones y cierra la de otro dispositivo', () => {
    TestBed.configureTestingModule({ imports: [SessionsListComponent], providers: [provideHttpClient(), provideHttpClientTesting()] });
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(SessionsListComponent);
    const el = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
    const sessions = [
      { id: 'a', device: 'Chrome en Windows', ipAddress: '10.0.0.1', createdAt: '2026-10-29T10:00:00Z', lastUsedAt: '2026-10-29T11:00:00Z', current: true },
      { id: 'b', device: 'Safari en iPhone', ipAddress: null, createdAt: '2026-10-28T10:00:00Z', lastUsedAt: '2026-10-28T12:00:00Z', current: false },
    ];
    http.expectOne('/api/v1/auth/sessions').flush(sessions);
    fixture.detectChanges();
    expect(el.textContent).toContain('(este dispositivo)');
    expect(el.querySelectorAll('li button').length).toBe(1);

    el.querySelector<HTMLButtonElement>('li button')!.click();
    http.expectOne({ method: 'DELETE', url: '/api/v1/auth/sessions/b' }).flush(null);
    http.expectOne('/api/v1/auth/sessions').flush([sessions[0]]);
    fixture.detectChanges();
    expect(el.textContent).toContain('Cerramos la sesión de Safari en iPhone.');
    expect(el.textContent).not.toContain('Cerrar todas las demás');
  });
});
