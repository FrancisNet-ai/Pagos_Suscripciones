import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { LoginPage } from './login.page';

describe('LoginPage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [LoginPage],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function render() {
    const fixture = TestBed.createComponent(LoginPage);
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  function fill(el: HTMLElement, selector: string, value: string) {
    const input = el.querySelector<HTMLInputElement>(selector)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  it('valida los campos sin llamar a la API', async () => {
    const { fixture, el } = render();
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(el.querySelectorAll('.field-error').length).toBe(2);
    http.expectNone('/api/v1/auth/login');
  });

  it('muestra el error de credenciales devuelto por el backend', async () => {
    const { fixture, el } = render();
    fill(el, '#email', 'ana@ejemplo.pe');
    fill(el, '#password', 'incorrecta');
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();

    const req = http.expectOne('/api/v1/auth/login');
    expect(req.request.body).toEqual({ email: 'ana@ejemplo.pe', password: 'incorrecta' });
    req.flush(
      { code: 'INVALID_CREDENTIALS', detail: 'Correo o contraseña incorrectos.' },
      { status: 401, statusText: 'Unauthorized' },
    );
    await fixture.whenStable();
    fixture.detectChanges();
    expect(el.querySelector('.notice-error')?.textContent).toContain('Correo o contraseña incorrectos.');
  });

  it('redirige al panel tras un login correcto', async () => {
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    const { el } = render();
    fill(el, '#email', 'ana@ejemplo.pe');
    fill(el, '#password', 'clave-segura-123');
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();

    http.expectOne('/api/v1/auth/login').flush({
      user: { id: '1', fullName: 'Ana', email: 'ana@ejemplo.pe', phone: '+51987654321', roles: ['BUSINESS_OWNER'] },
      business: { id: 'b', uniqueCode: 'NEG-1', slug: 'ana', businessName: 'Ana', status: 'ACTIVE', access: { active: true, block: null, message: null, subscriptionStatus: 'ACTIVE', planName: 'Base', expiresAt: null, usableUntil: null, features: [] } },
    });
    expect(navigate).toHaveBeenCalledWith('/panel');
  });
});
