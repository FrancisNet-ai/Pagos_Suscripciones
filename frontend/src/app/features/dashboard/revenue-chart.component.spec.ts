import { TestBed } from '@angular/core/testing';
import { RevenueChartComponent } from './revenue-chart.component';

describe('Gráfico de ingresos', () => {
  function render() {
    const fixture = TestBed.createComponent(RevenueChartComponent);
    fixture.componentRef.setInput('days', [
      { date: '2026-11-07', amount: 0.1, count: 1 },
      { date: '2026-11-08', amount: 0.2, count: 1 },
      { date: '2026-11-09', amount: 30, count: 2 },
    ]);
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('suma céntimos y permite consultar un día sin cambiar el total del periodo', () => {
    const { fixture, el } = render();
    expect(el.querySelector('.revenue-total')?.textContent).toContain('30.30');
    expect(el.querySelector('.revenue-total')?.textContent).toContain('4 cobros verificados');
    const slider = el.querySelector<HTMLInputElement>('input[type=range]')!;
    slider.value = '1';
    slider.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(el.querySelector('.revenue-selection')?.textContent).toContain('8 de noviembre de 2026');
    expect(el.querySelector('.revenue-selection')?.textContent).toContain('0.20');
    expect(el.querySelector('.revenue-total')?.textContent).toContain('30.30');
    expect(slider.getAttribute('aria-valuetext')).toContain('0.20');
    expect(el.querySelectorAll('tbody tr')).toHaveLength(3);
  });

  it('ajusta la selección al reducir el periodo y conserva los datos de la tabla', () => {
    const { fixture, el } = render();
    fixture.componentRef.setInput('days', [{ date: '2026-11-09', amount: 45, count: 3 }]);
    fixture.detectChanges();
    const slider = el.querySelector<HTMLInputElement>('input[type=range]')!;
    expect(slider.value).toBe('0');
    expect(slider.max).toBe('0');
    expect(el.querySelector('.revenue-selection')?.textContent).toContain('45.00');
    expect(el.querySelector('polyline')?.getAttribute('points')).toBe('300,26');
  });

  it('muestra un estado vacío real, sin inventar curvas, cuando no hay cobros', () => {
    const { fixture, el } = render();
    fixture.componentRef.setInput('days', [{ date: '2026-11-09', amount: 0, count: 0 }]);
    fixture.detectChanges();
    expect(el.querySelector('.revenue-total')?.textContent).toContain('0.00');
    expect(el.textContent).toContain('Aún no hay cobros verificados');
    expect(el.querySelector('polyline')).toBeNull();
    expect(el.querySelectorAll('tbody tr')).toHaveLength(1);
  });
});
