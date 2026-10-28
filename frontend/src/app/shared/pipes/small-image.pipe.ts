import { Pipe, PipeTransform } from '@angular/core';

const MEDIA = '/api/v1/public/media/';

/** Pide la versión reducida (640 px) de una imagen pública; si no existe, el servidor da el original. */
export function smallImage(url: string | null | undefined): string | null {
  if (!url) return null;
  return url.startsWith(MEDIA) && !url.includes('?') ? `${url}?size=sm` : url;
}

@Pipe({ name: 'smallImage' })
export class SmallImagePipe implements PipeTransform {
  transform(url: string | null | undefined): string | null {
    return smallImage(url);
  }
}
