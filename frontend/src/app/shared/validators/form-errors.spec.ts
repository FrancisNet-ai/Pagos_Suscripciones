import { FormControl, FormGroup, Validators } from '@angular/forms';
import { ApiError } from '../../core/services/api-error';
import { applyServerErrors, controlError } from './form-errors';

describe('controlError', () => {
  it('no muestra errores hasta que el usuario interactúa', () => {
    const control = new FormControl('', Validators.required);
    expect(controlError(control)).toBeNull();
    control.markAsTouched();
    expect(controlError(control)).toBe('Este campo es obligatorio');
  });

  it('usa un mensaje específico para casillas obligatorias', () => {
    const control = new FormControl(false, Validators.requiredTrue);
    control.markAsTouched();
    expect(controlError(control)).toBe('Debes aceptar para continuar');
  });

  it('aplica los errores de campo devueltos por la API', () => {
    const form = new FormGroup({ phone: new FormControl('999') });
    applyServerErrors(form, new ApiError(400, 'VALIDATION_ERROR', 'Revisa', [{ field: 'phone', message: 'Celular inválido' }]));
    expect(controlError(form.controls.phone)).toBe('Celular inválido');
  });
});
