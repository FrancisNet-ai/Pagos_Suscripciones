import { Pipe, PipeTransform } from '@angular/core';

const FORMAT = new Intl.NumberFormat('es-PE', { style: 'currency', currency: 'PEN', minimumFractionDigits: 2 });

/** Importe en soles peruanos: 15 → "S/ 15.00". */
@Pipe({ name: 'soles' })
export class SolesPipe implements PipeTransform {
  transform(value: number | null | undefined): string {
    return value === null || value === undefined ? '' : FORMAT.format(value);
  }
}

/** Duración legible: 30 → "30 días", 365 → "1 año". */
@Pipe({ name: 'duration' })
export class DurationPipe implements PipeTransform {
  transform(days: number | null | undefined): string {
    if (!days) return 'Sin duración';
    if (days % 365 === 0) return days === 365 ? '1 año' : `${days / 365} años`;
    if (days % 30 === 0 && days < 365) return days === 30 ? '1 mes' : `${days / 30} meses`;
    return days === 1 ? '1 día' : `${days} días`;
  }
}
