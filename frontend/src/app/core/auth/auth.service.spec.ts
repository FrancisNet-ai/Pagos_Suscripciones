import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from './auth.service';

describe('Arranque de sesión', () => {
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('consulta CSRF y sesión en paralelo y espera ambas antes de abrir la aplicación', async () => {
    const auth = TestBed.inject(AuthService);
    let ready = false;
    const initialization = auth.initialize().then(() => {
      ready = true;
    });
    const csrf = http.expectOne('/api/v1/auth/csrf');
    const session = http.expectOne('/api/v1/auth/me');
    session.flush({ user: { roles: ['BUSINESS_OWNER'] }, business: null });
    await Promise.resolve();
    expect(ready).toBe(false);
    csrf.flush(null, { status: 204, statusText: 'No Content' });
    await initialization;
    expect(ready).toBe(true);
    expect(auth.isAuthenticated()).toBe(true);
  });

  it('permite entrar como visitante si no existe sesión y falla la consulta de CSRF', async () => {
    const auth = TestBed.inject(AuthService);
    const initialization = auth.initialize();
    http.expectOne('/api/v1/auth/me').flush({}, { status: 401, statusText: 'Unauthorized' });
    http.expectOne('/api/v1/auth/csrf').error(new ProgressEvent('error'));
    await initialization;
    expect(auth.isAuthenticated()).toBe(false);
  });
});
