import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

// Pruebas visuales de espera: respuestas controladas, sin cuentas ni operaciones comerciales.
test.beforeEach(async ({ page, baseURL }) => {
  if (!baseURL || !['localhost', '127.0.0.1'].includes(new URL(baseURL).hostname)) {
    throw new Error('Estas pruebas solo se ejecutan contra una aplicación local.');
  }
  await page.route('**/api/v1/auth/me', (route) => route.fulfill({ status: 401, json: {} }));
  await page.route('**/api/v1/public/saas-plans', (route) => route.fulfill({ json: [] }));
});

test('el arranque muestra solo el monograma hasta recuperar la sesión', async ({ page }, info) => {
  let release!: () => void;
  const pending = new Promise<void>((resolve) => {
    release = resolve;
  });
  await page.route('**/api/v1/auth/csrf', async (route) => {
    await pending;
    await route.fulfill({ status: 204 });
  });
  await page.goto('/', { waitUntil: 'commit' });
  try {
    const loader = page.getByRole('status', { name: 'Cargando Nuvyra…', exact: true });
    await expect(loader).toBeVisible();
    await expect(loader).toHaveText('');
    await expect(loader.locator('img')).toBeVisible();
    const bounds = (await loader.locator('img').boundingBox())!;
    const viewport = page.viewportSize()!;
    expect(Math.abs(bounds.x + bounds.width / 2 - viewport.width / 2)).toBeLessThan(1);
    expect(Math.abs(bounds.y + bounds.height / 2 - viewport.height / 2)).toBeLessThan(1);
    const accessibility = await new AxeBuilder({ page })
      .include('[data-bootstrap-loader]')
      .analyze();
    expect(accessibility.violations).toEqual([]);
    await page.screenshot({ path: info.outputPath('monograma-arranque.png') });
  } finally {
    release();
  }
  await expect(page.getByRole('heading', { name: 'Vende, crece y conecta.' })).toBeVisible();
  await expect(page.locator('[data-bootstrap-loader]')).toHaveCount(0);
});

test('la espera de la tienda termina también cuando el servidor devuelve un error', async ({
  page,
}) => {
  await page.route('**/api/v1/auth/csrf', (route) => route.fulfill({ status: 204 }));
  let release!: () => void;
  const pending = new Promise<void>((resolve) => {
    release = resolve;
  });
  await page.route('**/api/v1/public/stores/carga-prueba/products', (route) =>
    route.fulfill({ json: [] }),
  );
  await page.route('**/api/v1/public/stores/carga-prueba', async (route) => {
    await pending;
    await route.fulfill({ status: 503, json: { detail: 'No se pudo conectar con la tienda.' } });
  });
  await page.goto('/tienda/carga-prueba');
  try {
    const loader = page.getByRole('status', { name: 'Cargando tienda…', exact: true });
    await expect(loader).toBeVisible();
    await expect(loader).toHaveText('');
    const bounds = (await loader.locator('img').boundingBox())!;
    const viewport = page.viewportSize()!;
    expect(Math.abs(bounds.x + bounds.width / 2 - viewport.width / 2)).toBeLessThan(1);
    expect(Math.abs(bounds.y + bounds.height / 2 - viewport.height / 2)).toBeLessThan(1);
  } finally {
    release();
  }
  await expect(page.getByRole('heading', { name: 'No pudimos cargar la tienda' })).toBeVisible();
  await expect(page.locator('app-loading-indicator')).toHaveCount(0);
});

test('el arco gira y se queda quieto si el dispositivo pide movimiento reducido', async ({
  page,
}) => {
  await page.emulateMedia({ reducedMotion: 'no-preference' });
  await page.goto('/loading-mark.svg');
  const arc = page.locator('.arc');
  await expect(arc).toHaveCSS('animation-duration', '2.4s');
  const start = await arc.evaluate((el) => getComputedStyle(el).transform);
  await expect.poll(() => arc.evaluate((el) => getComputedStyle(el).transform)).not.toBe(start);
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await expect(arc).toHaveCSS('animation-name', 'none');
});
