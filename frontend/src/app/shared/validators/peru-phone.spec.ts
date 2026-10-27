import { FormControl } from '@angular/forms';
import { normalizePeruMobile, peruMobileValidator } from './peru-phone';

describe('normalizePeruMobile', () => {
  it.each(['987654321', '987 654 321', '+51 987-654-321', '51987654321', '0051987654321'])('normaliza %s', (input) => {
    expect(normalizePeruMobile(input)).toBe('+51987654321');
  });

  it.each(['', '12345', '887654321', '9876543210', '+52987654321', '98765432a'])('rechaza %s', (input) => {
    expect(normalizePeruMobile(input)).toBeNull();
  });

  it('el validador ignora valores vacíos (los controla required)', () => {
    expect(peruMobileValidator(new FormControl(''))).toBeNull();
    expect(peruMobileValidator(new FormControl('123'))).toEqual({ peruMobile: true });
  });
});
