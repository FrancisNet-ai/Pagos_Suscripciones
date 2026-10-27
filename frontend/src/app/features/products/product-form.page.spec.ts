import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { ProductFormPage } from './product-form.page';

describe('ProductFormPage (nuevo)', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ProductFormPage],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function render() {
    const fixture = TestBed.createComponent(ProductFormPage);
    fixture.detectChanges();
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  function type(el: HTMLElement, selector: string, value: string) {
    const input = el.querySelector<HTMLInputElement>(selector)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  it('una suscripción exige duración y el control de cupos exige capacidad', async () => {
    const { fixture, el } = await render();
    type(el, '#name', 'Servicio Premium');
    type(el, '#price', '15');
    type(el, '#durationDays', '');
    el.querySelector<HTMLInputElement>('input[formcontrolname=capacityEnabled]')!.click();
    fixture.detectChanges();
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    fixture.detectChanges();

    const errors = Array.from(el.querySelectorAll('.field-error')).map((e) => e.textContent?.trim());
    expect(errors).toContain('Este campo es obligatorio');
    expect(errors.length).toBe(2);
    http.expectNone('/api/v1/products');
  });

  it('envía el producto con el precio redondeado y redirige a su edición', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const { el } = await render();
    type(el, '#name', 'Servicio Premium');
    type(el, '#price', '15.5');
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();

    const req = http.expectOne('/api/v1/products');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toMatchObject({ name: 'Servicio Premium', price: 15.5, productType: 'SUBSCRIPTION', durationDays: 30, capacityEnabled: false, capacity: null });
    req.flush({ id: 'abc' });
    expect(navigate).toHaveBeenCalledWith(['/panel/productos', 'abc'], { state: { created: true } });
  });
});
