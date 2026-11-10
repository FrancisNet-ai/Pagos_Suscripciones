import { Pipe, PipeTransform } from '@angular/core';

const ESCAPES: Record<string, string> = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };

/**
 * Texto que escribe el vendedor (descripción de la tienda o de un producto) a HTML seguro: escapa todo y solo
 * convierte `**negrita**` y los saltos de línea. Se usa con `[innerHTML]`, que además pasa por el sanitizador.
 */
export function richText(value: string | null | undefined): string {
  if (!value) return '';
  return value
    .replace(/[&<>"']/g, (c) => ESCAPES[c]!)
    .replace(/\*\*([^*\n]+?)\*\*/g, '<strong>$1</strong>')
    .replace(/\r?\n/g, '<br>');
}

/** Para vistas cortas (tarjetas): quita las marcas `**` y junta las líneas. */
export function plainText(value: string | null | undefined): string {
  if (!value) return '';
  return value.replace(/\*\*([^*\n]+?)\*\*/g, '$1').replace(/\s*\r?\n\s*/g, ' ').trim();
}

@Pipe({ name: 'richText' })
export class RichTextPipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    return richText(value);
  }
}

@Pipe({ name: 'plainText' })
export class PlainTextPipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    return plainText(value);
  }
}
