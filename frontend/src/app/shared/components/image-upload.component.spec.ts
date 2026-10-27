import { TestBed } from '@angular/core/testing';
import { ImageUploadComponent } from './image-upload.component';

describe('ImageUploadComponent', () => {
  function setup() {
    const fixture = TestBed.createComponent(ImageUploadComponent);
    fixture.componentRef.setInput('label', 'Logotipo');
    fixture.detectChanges();
    const selected: File[] = [];
    fixture.componentInstance.selected.subscribe((f) => selected.push(f));
    const input = (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>('input[type=file]')!;
    const choose = (file: File) => {
      Object.defineProperty(input, 'files', { value: [file], configurable: true });
      input.dispatchEvent(new Event('change'));
      fixture.detectChanges();
    };
    return { fixture, selected, choose };
  }

  it('emite archivos válidos', () => {
    const { selected, choose } = setup();
    choose(new File(['x'], 'logo.png', { type: 'image/png' }));
    expect(selected.length).toBe(1);
  });

  it('rechaza formatos no permitidos y archivos de más de 2 MB', () => {
    const { fixture, selected, choose } = setup();
    choose(new File(['<svg/>'], 'logo.svg', { type: 'image/svg+xml' }));
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Formato no permitido');
    choose(new File([new Uint8Array(2 * 1024 * 1024 + 1)], 'big.png', { type: 'image/png' }));
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('supera los 2 MB');
    expect(selected.length).toBe(0);
  });
});
