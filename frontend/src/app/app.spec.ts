import { TestBed } from '@angular/core/testing';
import {
  NavigationCancel,
  NavigationEnd,
  NavigationError,
  NavigationSkipped,
  NavigationStart,
  Router,
  provideRouter,
} from '@angular/router';
import { Subject } from 'rxjs';
import { App } from './app';

describe('Indicador de navegación', () => {
  const events = new Subject<
    NavigationStart | NavigationEnd | NavigationCancel | NavigationError | NavigationSkipped
  >();

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({ imports: [App], providers: [provideRouter([])] });
    vi.spyOn(TestBed.inject(Router), 'events', 'get').mockReturnValue(events);
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.useRealTimers();
  });

  function render() {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('no muestra una pantalla de carga en una navegación rápida', () => {
    const { fixture, el } = render();
    events.next(new NavigationStart(1, '/ingresar'));
    vi.advanceTimersByTime(100);
    events.next(new NavigationEnd(1, '/ingresar', '/ingresar'));
    vi.advanceTimersByTime(200);
    fixture.detectChanges();
    expect(el.querySelector('app-loading-indicator')).toBeNull();
    expect(el.querySelector('[inert]')).toBeNull();
  });

  it('muestra solo el monograma mientras espera y lo retira al terminar, sin duración mínima', () => {
    const { fixture, el } = render();
    events.next(new NavigationStart(2, '/panel'));
    vi.advanceTimersByTime(150);
    fixture.detectChanges();
    const loader = el.querySelector('app-loading-indicator')!;
    expect(loader.getAttribute('aria-label')).toBe('Cargando página…');
    expect(loader.textContent?.trim()).toBe('');
    expect(el.querySelector('[inert]')).not.toBeNull();

    events.next(new NavigationEnd(2, '/panel', '/panel'));
    fixture.detectChanges();
    expect(el.querySelector('app-loading-indicator')).toBeNull();
    expect(el.querySelector('[inert]')).toBeNull();
  });

  it.each([
    new NavigationCancel(3, '/panel', 'Redirección al acceso'),
    new NavigationError(3, '/panel', new Error('No se pudo descargar la ruta')),
    new NavigationSkipped(3, '/panel', 'La ruta ya está abierta'),
  ])('recupera el contenido si la navegación termina con $constructor.name', (end) => {
    const { fixture, el } = render();
    events.next(new NavigationStart(3, '/panel'));
    vi.advanceTimersByTime(150);
    fixture.detectChanges();
    expect(el.querySelector('app-loading-indicator')).not.toBeNull();
    events.next(end);
    fixture.detectChanges();
    expect(el.querySelector('app-loading-indicator')).toBeNull();
    expect(el.querySelector('[inert]')).toBeNull();
  });
});
