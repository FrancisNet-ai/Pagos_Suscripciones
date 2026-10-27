import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { SubscriptionItemComponent } from './subscription-item.component';
import { Subscription } from './subscriptions.api';

const pending: Subscription = {
  id: 's1', customerId: 'c1', customerName: 'Carlos', customerPhone: '+51987654321', productId: 'p1', productName: 'Servicio Premium',
  orderId: 'o1', origin: 'ORDER', status: 'PENDING_ACTIVATION', statusLabel: 'Por activar', durationDays: 30, contractedPrice: 15,
  paidAt: '2026-10-20T10:00:00Z', startAt: null, expiresAt: null, daysRemaining: null, capacityStatus: 'OCCUPIED',
  cancellationReason: null, notes: null, createdAt: '2026-10-20T10:00:00Z',
};

describe('SubscriptionItemComponent', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [SubscriptionItemComponent], providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });

  function render(sub: Subscription) {
    const fixture = TestBed.createComponent(SubscriptionItemComponent);
    fixture.componentRef.setInput('sub', sub);
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  const button = (el: HTMLElement, text: string) => Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.includes(text));

  it('activar con una fecha pasada envía ese inicio real', () => {
    const { fixture, el } = render(pending);
    const changed: Subscription[] = [];
    fixture.componentInstance.changed.subscribe((s) => changed.push(s));
    button(el, 'Registrar activación')!.click();
    fixture.detectChanges();
    const input = el.querySelector<HTMLInputElement>('input[type=date]')!;
    input.value = '2026-10-21';
    input.dispatchEvent(new Event('input'));
    button(el, 'Confirmar activación')!.click();

    const req = http.expectOne('/api/v1/subscriptions/s1/activate');
    expect(new Date(req.request.body.startAt).getDate()).toBe(21);
    req.flush({ ...pending, status: 'ACTIVE', statusLabel: 'Activa' });
    expect(changed[0].status).toBe('ACTIVE');
  });

  it('solo ofrece liberar el cupo cuando la suscripción terminó y el cupo está pendiente', () => {
    expect(button(render(pending).el, 'retiré el acceso')).toBeUndefined();
    const ended = render({ ...pending, status: 'EXPIRED', statusLabel: 'Vencida', capacityStatus: 'RELEASE_PENDING', startAt: '2026-09-01T00:00:00Z', expiresAt: '2026-10-01T00:00:00Z' });
    expect(button(ended.el, 'retiré el acceso')).toBeDefined();
    expect(ended.el.textContent).toContain('Cupo pendiente de liberar');
  });
});
