/**
 * Tema de la tienda a partir del color elegido por el vendedor. El texto sobre el color se
 * elige (blanco o azul noche) según su luminancia; si ninguno alcanza contraste WCAG AA (4.5:1),
 * el color se oscurece gradualmente hasta que el texto blanco sea legible.
 */
export interface StoreTheme {
  '--store-accent': string;
  '--store-on-accent': string;
}

const DEFAULT_ACCENT = '#1A3D63';
const WHITE = '#FFFFFF';
const NIGHT = '#0A1931';
const AA = 4.5;

export function storeTheme(color: string | null | undefined): StoreTheme {
  let accent = /^#[0-9a-f]{6}$/i.test(color ?? '') ? color!.toUpperCase() : DEFAULT_ACCENT;
  if (contrastRatio(accent, WHITE) >= AA) return { '--store-accent': accent, '--store-on-accent': WHITE };
  if (contrastRatio(accent, NIGHT) >= AA) return { '--store-accent': accent, '--store-on-accent': NIGHT };
  while (contrastRatio(accent, WHITE) < AA) accent = darken(accent, 0.06);
  return { '--store-accent': accent, '--store-on-accent': WHITE };
}

export function contrastRatio(a: string, b: string): number {
  const [l1, l2] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (l1 + 0.05) / (l2 + 0.05);
}

function channels(hex: string): number[] {
  return [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16));
}

function luminance(hex: string): number {
  const [r, g, b] = channels(hex).map((v) => v / 255).map((c) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4));
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function darken(hex: string, amount: number): string {
  return '#' + channels(hex).map((v) => Math.max(0, Math.round(v * (1 - amount))).toString(16).padStart(2, '0')).join('').toUpperCase();
}
