import { AbstractControl, FormGroup } from '@angular/forms';
import { ApiError } from '../../core/services/api-error';

const MESSAGES: Record<string, (args: unknown) => string> = {
  required: () => 'Este campo es obligatorio',
  requiredTrue: () => 'Debes aceptar para continuar',
  email: () => 'Ingresa un correo electrónico válido',
  minlength: (a) => `Debe tener al menos ${(a as { requiredLength: number }).requiredLength} caracteres`,
  maxlength: (a) => `Debe tener como máximo ${(a as { requiredLength: number }).requiredLength} caracteres`,
  peruMobile: () => 'Ingresa un celular peruano válido de 9 dígitos',
  passwordMismatch: () => 'Las contraseñas no coinciden',
  server: (a) => String(a),
};

/** Primer mensaje de error legible de un control, solo si el usuario ya interactuó con él. */
export function controlError(control: AbstractControl | null): string | null {
  if (!control || !control.errors || !(control.touched || control.dirty)) return null;
  let [key, args] = Object.entries(control.errors)[0];
  // Validators.requiredTrue reporta `required`; en casillas se muestra un mensaje específico.
  if (key === 'required' && control.value === false) key = 'requiredTrue';
  return MESSAGES[key]?.(args) ?? 'Valor inválido';
}

/** Lleva los errores de campo devueltos por la API a los controles del formulario. */
export function applyServerErrors(form: FormGroup, error: ApiError): void {
  for (const fieldError of error.fieldErrors) {
    const control = form.get(fieldError.field);
    if (control) {
      control.setErrors({ ...(control.errors ?? {}), server: fieldError.message });
      control.markAsTouched();
    }
  }
}
