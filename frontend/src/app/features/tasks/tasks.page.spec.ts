import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { SessionResponse } from '../../core/auth/auth.models';
import { AuthService } from '../../core/auth/auth.service';
import { TaskList } from './tasks.api';
import { TasksPage } from './tasks.page';

function session(role: 'OWNER' | 'STAFF'): SessionResponse {
  return {
    user: { id: role === 'OWNER' ? 'owner' : 'staff', fullName: 'Ana', email: 'a@ejemplo.pe', phone: '+51987654321', roles: [role === 'OWNER' ? 'BUSINESS_OWNER' : 'BUSINESS_STAFF'] },
    business: {
      id: 'b', uniqueCode: 'NV-1', slug: 'norte', businessName: 'Norte', status: 'ACTIVE', role,
      permissions: role === 'OWNER' ? ['PRODUCTS', 'ORDERS', 'CUSTOMERS', 'RENEWALS', 'REPORTS'] : ['ORDERS'],
      access: { active: true, block: null, message: null, subscriptionStatus: 'ACTIVE', planName: 'Crece', expiresAt: null, usableUntil: null, features: [] },
    },
  };
}

const list: TaskList = {
  items: [
    {
      id: 't1', type: 'VERIFY_PAYMENT', typeLabel: 'Verificar pago', since: '2026-10-26T10:00:00Z', dueAt: '2026-10-27T10:00:00Z', overdue: true,
      assignee: null, customerId: 'c1', customerName: 'Carlos Ramírez', productName: null, orderId: 'o1', orderNumber: 'NV-00012', amount: 15,
      subscriptionId: null, detail: null,
    },
  ],
  counts: { open: 1, mine: 0, unassigned: 1, overdue: 1 },
  members: [
    { userId: 'owner', fullName: 'Ana Titular', owner: true, types: ['VERIFY_PAYMENT', 'ACTIVATE_SERVICE'] },
    { userId: 'staff', fullName: 'Luis Apoyo', owner: false, types: ['VERIFY_PAYMENT'] },
    { userId: 'other', fullName: 'Rosa Clientes', owner: false, types: ['ACTIVATE_SERVICE'] },
  ],
};

function render(role: 'OWNER' | 'STAFF') {
  TestBed.configureTestingModule({ imports: [TasksPage], providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
  TestBed.inject(AuthService).startSession(session(role));
  const http = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(TasksPage);
  fixture.detectChanges();
  http.expectOne((r) => r.url === '/api/v1/tasks').flush(list);
  fixture.detectChanges();
  return { http, el: fixture.nativeElement as HTMLElement };
}

describe('TasksPage', () => {
  it('muestra el caso, el plazo vencido y enlaza al pedido donde se resuelve', () => {
    const { el } = render('OWNER');
    const task = el.querySelector('[data-testid="task-VERIFY_PAYMENT"]')!;
    expect(task.textContent).toContain('Carlos Ramírez');
    expect(task.textContent).toContain('Pedido NV-00012');
    expect(task.textContent).toContain('Plazo vencido');
    expect(task.querySelector('a')!.getAttribute('href')).toBe('/panel/ventas/o1');
    // No existe un botón para «marcar como hecho».
    expect(task.textContent).not.toContain('Marcar');
  });

  it('el titular solo puede asignar a quien tiene acceso a esa sección', () => {
    const { http, el } = render('OWNER');
    const options = Array.from(el.querySelectorAll('option')).map((o) => o.textContent?.trim());
    expect(options).toEqual(['Sin asignar', 'Ana Titular (titular)', 'Luis Apoyo']);
    const select = el.querySelector('select')!;
    select.value = 'staff';
    select.dispatchEvent(new Event('change'));
    const req = http.expectOne({ method: 'PUT', url: '/api/v1/tasks/t1/assignee' });
    expect(req.request.body).toEqual({ userId: 'staff' });
  });

  it('el colaborador toma la tarea para sí', () => {
    const { http, el } = render('STAFF');
    expect(el.querySelector('select')).toBeNull();
    Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.trim() === 'Tomar')!.click();
    expect(http.expectOne({ method: 'PUT', url: '/api/v1/tasks/t1/assignee' }).request.body).toEqual({ userId: 'staff' });
  });
});
