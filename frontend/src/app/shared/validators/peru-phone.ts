import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/**
 * Misma regla que el backend (`PeruPhone`): celular peruano de 9 dígitos que empieza en 9,
 * con o sin prefijo +51. El backend vuelve a validar y normaliza siempre.
 */
export function normalizePeruMobile(raw: string | null | undefined): string | null {
  if (!raw) return null;
  let digits = raw.replace(/[\s\-().]/g, '');
  if (digits.startsWith('+')) digits = digits.slice(1);
  if (digits.startsWith('0051')) digits = digits.slice(4);
  if (!/^\d+$/.test(digits)) return null;
  if (digits.length === 11 && digits.startsWith('51')) digits = digits.slice(2);
  return digits.length === 9 && digits.startsWith('9') ? `+51${digits}` : null;
}

export const peruMobileValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const value = control.value as string | null;
  if (!value) return null;
  return normalizePeruMobile(value) ? null : { peruMobile: true };
};
