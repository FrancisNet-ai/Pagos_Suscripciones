import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { LegalPage } from './legal.page';
import { legalDocs } from './legal-documents';

describe('LegalPage', () => {
  function render(documento: string) {
    TestBed.configureTestingModule({ imports: [LegalPage], providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(LegalPage);
    fixture.componentRef.setInput('documento', documento);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('muestra todas las secciones de los términos', () => {
    const el = render('condiciones');
    expect(el.querySelector('h1')?.textContent).toContain('Términos y condiciones');
    expect(el.querySelectorAll('section').length).toBe(legalDocs()['condiciones'].sections.length);
    expect(el.textContent).toContain('Ley N.º 29571');
  });

  it('la política de privacidad informa derechos, proveedores y flujo transfronterizo', () => {
    const text = render('privacidad').textContent ?? '';
    expect(text).toContain('Ley N.º 29733');
    expect(text).toContain('flujo transfronterizo');
    expect(text).toContain('Autoridad Nacional de Protección de Datos Personales');
  });

  it('avisa mientras falten los datos del titular', () => {
    expect(render('privacidad').textContent).toContain('[por completar]');
  });

  it('un documento desconocido muestra «no encontrado»', () => {
    expect(render('otro').textContent).toContain('Documento no encontrado');
  });
});
