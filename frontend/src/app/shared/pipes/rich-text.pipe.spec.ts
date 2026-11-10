import { plainText, richText } from './rich-text.pipe';

describe('richText', () => {
  it('convierte **negritas** y saltos de línea y escapa el HTML', () => {
    expect(richText('**Crunchyroll Premium** 🍥\nAnimes sin anuncios')).toBe('<strong>Crunchyroll Premium</strong> 🍥<br>Animes sin anuncios');
    expect(richText('<img src=x onerror=alert(1)> **ok**')).toBe('&lt;img src=x onerror=alert(1)&gt; <strong>ok</strong>');
    expect(richText('2 ** 3 sin cerrar')).toBe('2 ** 3 sin cerrar');
    expect(richText(null)).toBe('');
  });

  it('plainText quita las marcas para las tarjetas', () => {
    expect(plainText('**Prime Video** 🎬\n\nPelículas y series')).toBe('Prime Video 🎬 Películas y series');
  });
});
