import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RegisterPage } from './register.page';

const plan = {
  id: 'p1', name: 'Bacan', description: null, price: 25, billingPeriod: 'MONTHLY', billingPeriodLabel: 'Mensual',
  productLimit: 30, customerLimit: 600, adminUserLimit: 3, featureReports: true, featureReminders: true, featureBranding: true,
  active: true, sortOrder: 2,
};

describe('RegisterPage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [RegisterPage],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  function render() {
    const fixture = TestBed.createComponent(RegisterPage);
    fixture.detectChanges();
    http.expectOne('/api/v1/public/saas-plans').flush([plan]);
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  function fill(el: HTMLElement, id: string, value: string) {
    const input = el.querySelector<HTMLInputElement>('#' + id)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  it('el paso 1 no avanza a los planes con datos incompletos', async () => {
    const { fixture, el } = render();
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(el.querySelector('#fullName')).not.toBeNull();
    expect(el.querySelector('app-plan-card')).toBeNull();
    expect(el.querySelectorAll('.field-error').length).toBeGreaterThan(0);
  });

  it('con los datos completos pasa al paso 2 y muestra los planes', async () => {
    const { fixture, el } = render();
    fill(el, 'fullName', 'Ana Pérez');
    fill(el, 'email', 'ana@ejemplo.pe');
    fill(el, 'phone', '987654321');
    fill(el, 'password', 'una-frase-segura');
    fill(el, 'businessName', 'Tienda Ana');
    fixture.detectChanges();
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(el.querySelector('#fullName')).toBeNull();
    expect(el.querySelector('app-plan-card')?.textContent).toContain('Bacan');
    expect(el.querySelector('[aria-current=step]')?.textContent).toContain('Tu plan');
    http.expectNone('/api/v1/auth/register');
  });

  it('el paso 2 exige elegir un plan y ofrece la prueba gratis de 14 días', async () => {
    const { fixture, el } = render();
    fill(el, 'fullName', 'Ana Pérez');
    fill(el, 'email', 'ana@ejemplo.pe');
    fill(el, 'phone', '987654321');
    fill(el, 'password', 'una-frase-segura');
    fill(el, 'businessName', 'Tienda Ana');
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(el.querySelector('.plan-card__trial')?.textContent).toContain('14 días gratis');
    el.querySelectorAll<HTMLInputElement>('input[type=checkbox]').forEach((c) => c.click());
    fixture.detectChanges();
    const submit = el.querySelector<HTMLButtonElement>('button[type=submit]')!;
    expect(submit.textContent).toContain('Empezar mis 14 días gratis');

    submit.click();
    fixture.detectChanges();
    expect(el.textContent).toContain('Elige un plan para empezar tu prueba gratis.');
    http.expectNone('/api/v1/auth/register');

    Array.from(el.querySelectorAll<HTMLButtonElement>('app-plan-card button')).find((b) => b.textContent?.includes('Elegir'))!.click();
    fixture.detectChanges();
    submit.click();
    const req = http.expectOne('/api/v1/auth/register');
    expect(req.request.body.planId).toBe('p1');
  });
});
