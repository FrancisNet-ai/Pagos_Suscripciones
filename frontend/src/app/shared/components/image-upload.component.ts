import { ChangeDetectionStrategy, Component, ElementRef, input, output, signal, viewChild } from '@angular/core';

const MAX_BYTES = 2 * 1024 * 1024;
const ACCEPTED = ['image/png', 'image/jpeg', 'image/webp'];

/**
 * Selector de imagen con vista previa. Solo valida tipo y tamaño para dar respuesta rápida;
 * el backend vuelve a validar el contenido real del archivo.
 */
@Component({
  selector: 'app-image-upload',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex flex-wrap items-center gap-5">
      <div
        class="flex shrink-0 items-center justify-center overflow-hidden border border-[var(--line-strong)] bg-[color-mix(in_srgb,var(--color-mist)_25%,white)]"
        [class.rounded-full]="shape() === 'circle'"
        [class.rounded-[var(--radius-panel)]]="shape() !== 'circle'"
        [style.width.px]="shape() === 'wide' ? 192 : 88"
        [style.height.px]="88"
      >
        @if (imageUrl()) {
          <img [src]="imageUrl()" [alt]="label()" class="h-full w-full object-cover" loading="lazy" />
        } @else {
          <span class="px-2 text-center text-xs text-[var(--text-muted)]">Sin imagen</span>
        }
      </div>
      <div class="grid gap-2">
        <div class="flex flex-wrap gap-2">
          <button type="button" class="btn btn-secondary !min-h-10" [disabled]="busy() || disabled()" (click)="input().nativeElement.click()">
            @if (busy()) { <span class="spinner" aria-hidden="true"></span> Subiendo… } @else { {{ imageUrl() ? 'Cambiar' : 'Subir' }} {{ noun() ?? label().toLowerCase() }} }
          </button>
          @if (imageUrl()) {
            <button type="button" class="btn !min-h-10 text-danger hover:bg-danger-soft" [disabled]="busy() || disabled()" (click)="remove.emit()">Quitar</button>
          }
        </div>
        <p class="field-hint">PNG, JPG o WebP · máximo 2 MB.</p>
        @if (localError() || error(); as message) {
          <p class="field-error" role="alert">{{ message }}</p>
        }
      </div>
      <input #file type="file" class="hidden" [accept]="accepted" (change)="onFile($event)" [attr.aria-label]="'Seleccionar ' + label().toLowerCase()" />
    </div>
  `,
})
export class ImageUploadComponent {
  readonly label = input.required<string>();
  /** Texto del botón cuando el rótulo no debe pasarse a minúsculas (p. ej. «QR»). */
  readonly noun = input<string>();
  readonly imageUrl = input<string | null>(null);
  readonly busy = input(false);
  readonly disabled = input(false);
  readonly error = input<string | null>(null);
  readonly shape = input<'square' | 'circle' | 'wide'>('square');

  readonly selected = output<File>();
  readonly remove = output<void>();

  protected readonly input = viewChild.required<ElementRef<HTMLInputElement>>('file');
  protected readonly localError = signal<string | null>(null);
  protected readonly accepted = ACCEPTED.join(',');

  protected onFile(event: Event): void {
    const target = event.target as HTMLInputElement;
    const file = target.files?.[0];
    target.value = '';
    if (!file) return;
    if (!ACCEPTED.includes(file.type)) {
      this.localError.set('Formato no permitido. Usa PNG, JPG o WebP.');
      return;
    }
    if (file.size > MAX_BYTES) {
      this.localError.set('La imagen supera los 2 MB.');
      return;
    }
    this.localError.set(null);
    this.selected.emit(file);
  }
}
