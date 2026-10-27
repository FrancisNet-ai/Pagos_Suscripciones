import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { CustomerImportPage } from './customer-import.page';
import { CustomerImport } from './imports.api';

const preview: CustomerImport = {
  id: 'imp1', fileName: 'clientes.csv', status: 'PREVIEW', totalRows: 2,
  summary: { toCreate: 1, existing: 0, withSubscription: 1, errors: 1 }, result: null, expiresAt: '2026-10-28T10:00:00Z',
  rows: [
    { rowNumber: 2, fullName: 'Ana Torres', phone: '+51987654321', notes: null, productId: 'p1', productName: 'Servicio Premium', startDate: '2026-10-01',
      durationDays: 30, price: 15, status: 'ACTIVE', action: 'CREATE', errors: [], outcome: null, outcomeMessage: null },
    { rowNumber: 3, fullName: 'Sin Celular', phone: null, notes: null, productId: null, productName: null, startDate: null,
      durationDays: null, price: null, status: null, action: 'ERROR', errors: ['Celular no válido: «123»'], outcome: null, outcomeMessage: null },
  ],
};

describe('CustomerImportPage', () => {
  it('muestra la vista previa con errores por fila y confirma la importación', () => {
    TestBed.configureTestingModule({ imports: [CustomerImportPage], providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(CustomerImportPage);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const input = el.querySelector<HTMLInputElement>('#import-file')!;
    const file = new File(['nombre;celular\n'], 'clientes.csv', { type: 'text/csv' });
    Object.defineProperty(input, 'files', { value: [file] });
    input.dispatchEvent(new Event('change'));
    const req = http.expectOne({ method: 'POST', url: '/api/v1/customers/imports' });
    expect(req.request.body instanceof FormData).toBe(true);
    req.flush(preview);
    fixture.detectChanges();
    expect(el.textContent).toContain('1 nuevos');
    expect(el.textContent).toContain('Celular no válido: «123»');

    Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.includes('Confirmar importación'))!.click();
    http.expectOne({ method: 'POST', url: '/api/v1/customers/imports/imp1/commit' }).flush({
      ...preview, status: 'COMMITTED', result: { created: 1, subscriptionsAdded: 1, unchanged: 0, failed: 0, skipped: 1 },
      rows: [{ ...preview.rows[0]!, outcome: 'CREATED' }, { ...preview.rows[1]!, outcome: 'SKIPPED' }],
    });
    fixture.detectChanges();
    expect(el.textContent).toContain('Importación aplicada: 1 cliente nuevo y 1 suscripción.');
    expect(el.textContent).toContain('Creado');
  });
});
