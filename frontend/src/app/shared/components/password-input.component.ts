import { ChangeDetectionStrategy, Component, forwardRef, input, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

/** Campo de contraseña con botón accesible para mostrar u ocultar el texto. */
@Component({
  selector: 'app-password-input',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => PasswordInputComponent), multi: true }],
  template: `
    <div class="relative">
      <input
        class="input pr-24"
        [id]="inputId()"
        [type]="visible() ? 'text' : 'password'"
        [attr.autocomplete]="autocomplete()"
        [attr.aria-invalid]="invalid()"
        [attr.aria-describedby]="describedBy() || null"
        [value]="value()"
        [disabled]="disabled()"
        (input)="onInput($event)"
        (blur)="onTouched()"
      />
      <button
        type="button"
        class="absolute inset-y-0 right-0 my-1 mr-1 rounded-[var(--radius-control)] px-3 text-sm font-semibold text-corporate hover:bg-mist/30"
        [attr.aria-pressed]="visible()"
        [attr.aria-controls]="inputId()"
        (click)="visible.set(!visible())"
      >
        {{ visible() ? 'Ocultar' : 'Mostrar' }}
      </button>
    </div>
  `,
})
export class PasswordInputComponent implements ControlValueAccessor {
  readonly inputId = input.required<string>();
  readonly autocomplete = input<'current-password' | 'new-password'>('current-password');
  readonly invalid = input(false);
  readonly describedBy = input<string>('');

  protected readonly visible = signal(false);
  protected readonly value = signal('');
  protected readonly disabled = signal(false);

  private onChange: (value: string) => void = () => {};
  protected onTouched: () => void = () => {};

  writeValue(value: string | null): void {
    this.value.set(value ?? '');
  }

  registerOnChange(fn: (value: string) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled.set(isDisabled);
  }

  protected onInput(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.value.set(value);
    this.onChange(value);
  }
}
