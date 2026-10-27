import { TestBed } from '@angular/core/testing';
import { NonNullableFormBuilder } from '@angular/forms';
import { manualSubscriptionForm, toManualPayload } from './manual-subscription-fields.component';

describe('manualSubscriptionForm', () => {
  it('convierte los valores al contrato del backend', () => {
    const form = manualSubscriptionForm(TestBed.inject(NonNullableFormBuilder));
    form.setValue({ productId: 'p1', startDate: '2026-09-15', durationDays: 90, contractedPrice: 39.999, status: 'ACTIVE', notes: '  perfil 3  ' });
    expect(toManualPayload(form)).toEqual({ productId: 'p1', startDate: '2026-09-15', durationDays: 90, contractedPrice: 40, status: 'ACTIVE', notes: 'perfil 3' });
  });

  it('exige producto, fecha, duración e importe', () => {
    const form = manualSubscriptionForm(TestBed.inject(NonNullableFormBuilder));
    form.patchValue({ durationDays: null, contractedPrice: null });
    expect(form.controls.productId.hasError('required')).toBe(true);
    expect(form.controls.startDate.hasError('required')).toBe(true);
    expect(form.controls.durationDays.hasError('required')).toBe(true);
    expect(form.controls.contractedPrice.hasError('required')).toBe(true);
  });
});
