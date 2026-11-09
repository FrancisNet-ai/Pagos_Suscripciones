import { TestBed } from '@angular/core/testing';
import { RailDotsComponent } from './rail-dots.component';

describe('RailDotsComponent', () => {
  it('muestra un punto por tarjeta y marca la que se ve', () => {
    const rail = document.createElement('div');
    for (let i = 0; i < 3; i++) rail.appendChild(document.createElement('article'));
    rail.scrollTo = () => undefined;
    const fixture = TestBed.createComponent(RailDotsComponent);
    fixture.componentRef.setInput('rail', rail);
    fixture.componentRef.setInput('count', 3);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const dots = Array.from(el.querySelectorAll<HTMLButtonElement>('button'));
    expect(dots.map((d) => d.getAttribute('aria-label'))).toEqual(['Plan 1 de 3', 'Plan 2 de 3', 'Plan 3 de 3']);
    expect(dots[0].getAttribute('aria-current')).toBe('true');

    dots[2].click();
    fixture.detectChanges();
    expect(dots[2].getAttribute('aria-current')).toBe('true');
    expect(dots[0].getAttribute('aria-current')).toBeNull();
  });
});
