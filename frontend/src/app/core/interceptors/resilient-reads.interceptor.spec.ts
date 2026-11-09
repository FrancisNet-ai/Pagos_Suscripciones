import {
  HttpClient,
  HttpErrorResponse,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { resilientReadsInterceptor } from './resilient-reads.interceptor';

describe('Recuperación limitada de lecturas', () => {
  let http: HttpTestingController;
  let client: HttpClient;
  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([resilientReadsInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    client = TestBed.inject(HttpClient);
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(true);
  });
  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  it('recupera una lectura tras un fallo temporal, conservando URL y parámetros', () => {
    const next = vi.fn();
    client.get('/api/v1/products', { params: { page: 2 } }).subscribe(next);
    http.expectOne('/api/v1/products?page=2').flush({}, { status: 503, statusText: 'Unavailable' });
    http.expectNone('/api/v1/products?page=2');
    vi.advanceTimersByTime(600);
    http.expectOne('/api/v1/products?page=2').flush({ items: [] });
    expect(next).toHaveBeenCalledWith({ items: [] });
  });

  it('se detiene después de un solo reintento', () => {
    const error = vi.fn();
    client.get('/api/v1/products').subscribe({ error });
    http.expectOne('/api/v1/products').error(new ProgressEvent('error'));
    vi.advanceTimersByTime(600);
    http.expectOne('/api/v1/products').error(new ProgressEvent('error'));
    vi.advanceTimersByTime(60_000);
    http.expectNone('/api/v1/products');
    expect(error).toHaveBeenCalledOnce();
  });

  it.each(['POST', 'PUT', 'PATCH', 'DELETE'])('nunca repite una operación %s', (method) => {
    const error = vi.fn();
    client.request(method, '/api/v1/payments/p1/verify', { body: {} }).subscribe({ error });
    http.expectOne('/api/v1/payments/p1/verify').error(new ProgressEvent('error'));
    vi.advanceTimersByTime(60_000);
    http.expectNone('/api/v1/payments/p1/verify');
    expect(error).toHaveBeenCalledOnce();
  });

  it.each([401, 403, 404, 409, 422, 429])('no reintenta un error HTTP %i', (status) => {
    const error = vi.fn();
    client.get('/api/v1/products').subscribe({ error });
    http.expectOne('/api/v1/products').flush({}, { status, statusText: 'Error' });
    vi.advanceTimersByTime(60_000);
    http.expectNone('/api/v1/products');
    expect(error).toHaveBeenCalledOnce();
  });

  it('no inicia un reintento mientras el navegador está desconectado', () => {
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false);
    const error = vi.fn();
    client.get('/api/v1/products').subscribe({ error });
    http.expectOne('/api/v1/products').error(new ProgressEvent('error'));
    vi.advanceTimersByTime(600);
    http.expectNone('/api/v1/products');
    expect(error).toHaveBeenCalledOnce();
  });

  it('no aplica el límite de lecturas JSON a una descarga de comprobante', () => {
    const next = vi.fn();
    client.get('/api/v1/payments/p1/receipt', { responseType: 'blob' }).subscribe(next);
    const download = http.expectOne('/api/v1/payments/p1/receipt');
    vi.advanceTimersByTime(60_000);
    expect(download.cancelled).toBe(false);
    download.flush(new Blob(['comprobante'], { type: 'image/png' }));
    expect(next).toHaveBeenCalledOnce();
  });

  it('cancela las lecturas colgadas y devuelve un error de conexión tras el límite', () => {
    let received: HttpErrorResponse | undefined;
    client.get('/api/v1/products').subscribe({
      error: (error) => {
        received = error;
      },
    });
    const first = http.expectOne('/api/v1/products');
    vi.advanceTimersByTime(30_000);
    expect(first.cancelled).toBe(true);
    vi.advanceTimersByTime(600);
    const second = http.expectOne('/api/v1/products');
    vi.advanceTimersByTime(30_000);
    expect(second.cancelled).toBe(true);
    expect(received?.status).toBe(0);
    http.expectNone('/api/v1/products');
  });
});
