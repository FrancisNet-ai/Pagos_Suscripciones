import { contrastRatio, storeTheme } from './store-theme';

describe('storeTheme', () => {
  it('usa texto blanco sobre colores oscuros y azul noche sobre colores claros', () => {
    expect(storeTheme('#1A3D63')['--store-on-accent']).toBe('#FFFFFF');
    expect(storeTheme('#F4D35E')['--store-on-accent']).toBe('#0A1931');
    expect(storeTheme('#1a3d63')['--store-accent']).toBe('#1A3D63');
  });

  it('el texto elegido siempre cumple contraste AA', () => {
    for (const color of ['#FF0000', '#00FF00', '#808080', '#B3CFE5', '#4A7EA7', '#101014', '#FFFFFF']) {
      const theme = storeTheme(color);
      expect(contrastRatio(theme['--store-accent'], theme['--store-on-accent'])).toBeGreaterThanOrEqual(4.5);
    }
  });

  it('oscurece colores intermedios hasta alcanzar AA con texto blanco', () => {
    const theme = storeTheme('#808080');
    expect(theme['--store-on-accent']).toBe('#FFFFFF');
    expect(theme['--store-accent']).not.toBe('#808080');
  });

  it('ignora colores inválidos', () => {
    expect(storeTheme('javascript:alert(1)')['--store-accent']).toBe('#1A3D63');
    expect(storeTheme(null)['--store-accent']).toBe('#1A3D63');
  });
});
