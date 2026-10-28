import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AccountPage } from './account.page';

function type(el: HTMLElement, id: string, value: string): void {
  const input = el.querySelector<HTMLInputElement>(`#${id}`)!;
  input.value = value;
  input.dispatchEvent(new Event('input'));
}

describe('AccountPage', () => {
  function setup() {
    TestBed.configureTestingModule({ imports: [AccountPage], providers: [provideHttpClient(), provideHttpClientTesting()] });
    const fixture = TestBed.createComponent(AccountPage);
    fixture.detectChanges();
    return { fixture, http: TestBed.inject(HttpTestingController), el: fixture.nativeElement as HTMLElement };
  }

  it('no envía si la confirmación no coincide', () => {
    const { fixture, http, el } = setup();
    type(el, 'currentPassword', 'clave-actual-segura');
    type(el, 'newPassword', 'clave-nueva-segura');
    type(el, 'confirm', 'otra-cosa-distinta');
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    fixture.detectChanges();
    http.expectNone('/api/v1/auth/password');
    expect(el.querySelector('#confirm-error')).not.toBeNull();
  });

  it('cambia la contraseña e informa cuántas sesiones cerró', () => {
    const { fixture, http, el } = setup();
    type(el, 'currentPassword', 'clave-actual-segura');
    type(el, 'newPassword', 'clave-nueva-segura');
    type(el, 'confirm', 'clave-nueva-segura');
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    const req = http.expectOne({ method: 'POST', url: '/api/v1/auth/password' });
    expect(req.request.body).toEqual({ currentPassword: 'clave-actual-segura', newPassword: 'clave-nueva-segura' });
    req.flush({ otherSessionsClosed: 2 });
    fixture.detectChanges();
    expect(el.textContent).toContain('Cerramos 2 sesiones en otros dispositivos.');
  });

  it('muestra el error del servidor en el campo de la contraseña actual', () => {
    const { fixture, http, el } = setup();
    type(el, 'currentPassword', 'equivocada-123');
    type(el, 'newPassword', 'clave-nueva-segura');
    type(el, 'confirm', 'clave-nueva-segura');
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    http.expectOne('/api/v1/auth/password').flush(
      { code: 'VALIDATION_ERROR', detail: 'Revisa los datos', errors: [{ field: 'currentPassword', message: 'La contraseña actual no es correcta' }] },
      { status: 400, statusText: 'Bad Request' },
    );
    fixture.detectChanges();
    expect(el.querySelector('#currentPassword-error')?.textContent).toContain('La contraseña actual no es correcta');
  });
});
