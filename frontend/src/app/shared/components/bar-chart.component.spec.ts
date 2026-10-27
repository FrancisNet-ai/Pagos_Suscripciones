import { TestBed } from '@angular/core/testing';
import { BarChartComponent } from './bar-chart.component';

describe('BarChartComponent', () => {
  it('dibuja una barra por día y ofrece un resumen accesible', () => {
    const fixture = TestBed.createComponent(BarChartComponent);
    fixture.componentRef.setInput('points', [
      { label: '1 de octubre', tick: '01', value: 0 },
      { label: '2 de octubre', tick: '02', value: 45 },
      { label: '3 de octubre', tick: '03', value: 15, highlight: true },
    ]);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelectorAll('rect').length).toBe(3);
    expect(el.querySelector('svg')!.getAttribute('aria-label')).toContain(
      'total S/ 60.00 en 3 días; mejor día 2 de octubre',
    );
    expect(el.querySelectorAll('table tr').length).toBe(3);
  });

  it('el gráfico de área conserva los importes y representa cero sobre la línea base', () => {
    const fixture = TestBed.createComponent(BarChartComponent);
    fixture.componentRef.setInput('variant', 'area');
    fixture.componentRef.setInput('points', [{ label: 'Hoy', tick: '27', value: 0 }]);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('polyline')!.getAttribute('points')).toBe('300,158');
    expect(el.querySelector('svg')!.getAttribute('aria-label')).toContain(
      'total S/ 0.00 en 1 días',
    );
    expect(el.querySelector('table')!.textContent).toMatch(/S\/\s0\.00/);
  });
});
