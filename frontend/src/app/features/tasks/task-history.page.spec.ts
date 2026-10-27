import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Page } from '../../core/services/page';
import { ResolvedTask } from './tasks.api';
import { TaskHistoryPage } from './task-history.page';

const entry: ResolvedTask = {
  task: {
    id: 't1',
    type: 'VERIFY_PAYMENT',
    typeLabel: 'Verificar pago',
    since: '2026-10-26T10:00:00Z',
    dueAt: '2026-10-27T10:00:00Z',
    overdue: false,
    assignee: { userId: 'owner', fullName: 'Ana Titular' },
    customerId: 'c1',
    customerName: 'Carlos',
    productName: null,
    orderId: 'o1',
    orderNumber: 'ORD-12',
    amount: 15,
    subscriptionId: null,
    detail: null,
  },
  resolvedAt: '2026-10-26T12:00:00Z',
  resolvedBy: { userId: 'staff', fullName: 'Luis Apoyo' },
  resolutionAction: 'PAYMENT_VERIFIED',
  resolutionLabel: 'Pago verificado',
};

function render() {
  TestBed.configureTestingModule({
    imports: [TaskHistoryPage],
    providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
  });
  const http = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(TaskHistoryPage);
  fixture.detectChanges();
  const request = http.expectOne((r) => r.url === '/api/v1/tasks/history');
  return { http, fixture, request, el: fixture.nativeElement as HTMLElement };
}

function result(items: ResolvedTask[] = [entry], page = 0): Page<ResolvedTask> {
  return { items, page, size: 20, totalItems: 21, totalPages: 2 };
}

describe('TaskHistoryPage', () => {
  it('distingue al autor real del responsable y permite pasar de página', () => {
    const { request, http, fixture, el } = render();
    request.flush(result());
    fixture.detectChanges();
    expect(el.textContent).toContain('Acción realizada por Luis Apoyo');
    expect(el.textContent).toContain('Responsable asignado: Ana Titular');
    expect(el.textContent).toContain('Pago verificado');
    const buttons = Array.from(el.querySelectorAll('button'));
    expect(buttons.find((b) => b.textContent?.trim() === 'Anterior')!.disabled).toBe(true);
    buttons.find((b) => b.textContent?.trim() === 'Siguiente')!.click();
    fixture.detectChanges();
    const next = http.expectOne(
      (r) => r.url === '/api/v1/tasks/history' && r.params.get('page') === '1',
    );
    next.flush(result([entry], 1));
    fixture.detectChanges();
    expect(el.textContent).toContain('Página 2 de 2');
    expect(
      Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.trim() === 'Siguiente')!
        .disabled,
    ).toBe(true);
    http.verify();
  });

  it('no atribuye un cambio sin evidencia al responsable asignado', () => {
    const { request, fixture, el } = render();
    request.flush(
      result([
        {
          ...entry,
          resolvedBy: null,
          resolutionAction: 'STATE_CHANGED',
          resolutionLabel: 'Cerrado por cambio de estado',
        },
      ]),
    );
    fixture.detectChanges();
    expect(el.textContent).toContain('Sin autor registrado');
    expect(el.textContent).not.toContain('Acción realizada por');
    expect(el.textContent).not.toContain('Marcar como');
  });

  it('presenta el estado vacío sin controles de paginación', () => {
    const { request, fixture, el } = render();
    request.flush({ ...result([]), totalItems: 0, totalPages: 0 });
    fixture.detectChanges();
    expect(el.textContent).toContain('Todavía no hay pendientes cerrados');
    expect(el.querySelector('nav')).toBeNull();
  });

  it('permite reintentar tras un error del servidor', () => {
    const { request, http, fixture, el } = render();
    request.flush({ message: 'No disponible' }, { status: 503, statusText: 'Unavailable' });
    fixture.detectChanges();
    expect(el.querySelector('[role="alert"]')).not.toBeNull();
    el.querySelector('button')!.click();
    fixture.detectChanges();
    http.expectOne((r) => r.url === '/api/v1/tasks/history').flush(result());
    fixture.detectChanges();
    expect(el.querySelector('[role="alert"]')).toBeNull();
    expect(el.textContent).toContain('Carlos');
    http.verify();
  });
});
