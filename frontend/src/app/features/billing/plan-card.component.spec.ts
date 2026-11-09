import { TestBed } from '@angular/core/testing';
import { Plan } from './billing.api';
import { PlanCardComponent } from './plan-card.component';

const plan: Plan = {
  id: 'p1',
  name: 'Básico',
  description: null,
  price: 14,
  billingPeriod: 'MONTHLY',
  billingPeriodLabel: 'Mensual',
  productLimit: 10,
  customerLimit: 100,
  adminUserLimit: 1,
  featureReports: true,
  featureReminders: false,
  featureBranding: false,
  active: true,
  sortOrder: 0,
};

describe('PlanCardComponent', () => {
  it('pone primero lo incluido y deja el resto tras «Más detalles»', () => {
    const fixture = TestBed.createComponent(PlanCardComponent);
    fixture.componentRef.setInput('plan', plan);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const items = Array.from(el.querySelectorAll('.plan-card__benefits li'));
    expect(items.map((li) => li.classList.contains('plan-card__missing'))).toEqual([false, false, false, false, true, true]);
    expect(el.querySelectorAll('.plan-card__extra').length).toBe(3);

    const more = el.querySelector<HTMLButtonElement>('.plan-card__more')!;
    expect(more.getAttribute('aria-expanded')).toBe('false');
    expect(more.getAttribute('aria-controls')).toBe(el.querySelector('.plan-card__benefits')!.id);
    more.click();
    fixture.detectChanges();
    expect(more.getAttribute('aria-expanded')).toBe('true');
    expect(more.textContent).toContain('Menos detalles');
    expect(el.querySelectorAll('.plan-card__extra').length).toBe(0);
  });
});
