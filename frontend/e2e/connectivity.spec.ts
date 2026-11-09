import { test, expect } from '@playwright/test';

// Cortes de red controlados: solo localhost, sin crear pedidos ni hacer transferencias.
test.beforeEach(async ({ page, baseURL }) => {
  if (!baseURL || !['localhost', '127.0.0.1'].includes(new URL(baseURL).hostname))
    throw new Error('Solo se permiten pruebas locales.');
  await page.route('**/api/v1/auth/csrf', (route) => route.fulfill({ status: 204 }));
  await page.route('**/api/v1/auth/me', (route) => route.fulfill({ status: 401, json: {} }));
  await page.route('**/api/v1/public/saas-plans', (route) => route.fulfill({ json: [] }));
});

const store = {
  slug: 'conexion-prueba',
  businessName: 'Tienda de prueba de conexión',
  logoUrl: null,
  coverUrl: null,
  primaryColor: null,
  description: null,
  acceptingOrders: true,
  notice: null,
  paymentMethods: [{ method: 'YAPE', label: 'Yape' }],
  orderUpdatesByWhatsapp: false,
};
const product = {
  id: 'p1',
  name: 'Producto de prueba de conexión',
  productType: 'ONE_TIME',
  price: 15,
  currency: 'PEN',
  durationDays: null,
  imageUrl: null,
  availableSlots: null,
  soldOut: false,
  purchasable: true,
};

test('recupera la tienda de un fallo temporal sin recargar la página', async ({ page }) => {
  let attempts = 0;
  await page.route('**/api/v1/public/stores/conexion-prueba', (route) => {
    attempts++;
    return attempts === 1
      ? route.fulfill({ status: 503, json: {} })
      : route.fulfill({ json: store });
  });
  await page.route('**/api/v1/public/stores/conexion-prueba/products', (route) =>
    route.fulfill({ json: [] }),
  );
  await page.goto('/tienda/conexion-prueba');
  await expect(page.getByRole('heading', { name: store.businessName })).toBeVisible();
  expect(attempts).toBe(2);
});

test('un corte conserva el formulario y no reenvía el pedido hasta que el comprador lo pide', async ({
  page,
  context,
}) => {
  await page.route('**/api/v1/public/stores/conexion-prueba', (route) =>
    route.fulfill({ json: store }),
  );
  await page.route('**/api/v1/public/stores/conexion-prueba/products/p1', (route) =>
    route.fulfill({ json: product }),
  );
  const keys: (string | undefined)[] = [];
  await page.route('**/api/v1/public/stores/conexion-prueba/orders', (route) => {
    keys.push(route.request().headers()['idempotency-key']);
    return route.abort('connectionreset');
  });
  await page.goto('/tienda/conexion-prueba/comprar/p1');
  await page.getByLabel('Tu nombre', { exact: true }).fill('Comprador de prueba');
  await page.getByLabel('Tu celular con WhatsApp').fill('987654321');
  await page.getByLabel(/Acepto los/).check();
  await page.getByLabel(/Autorizo a/).check();
  await context.setOffline(true);
  await expect(page.getByRole('status').filter({ hasText: 'Sin conexión.' })).toBeVisible();
  await expect(page.getByLabel('Tu nombre', { exact: true })).toHaveValue('Comprador de prueba');
  await context.setOffline(false);
  await expect(page.getByRole('status').filter({ hasText: 'Sin conexión.' })).toHaveCount(0);
  const submit = page.getByRole('button', { name: /Continuar al pago/ });
  await submit.click();
  await expect(page.getByRole('alert')).toContainText('No pudimos conectar');
  expect(keys).toHaveLength(1);
  await expect(page.getByLabel('Tu celular con WhatsApp')).toHaveValue('987654321');
  await submit.click();
  await expect(page.getByRole('alert')).toContainText('No pudimos conectar');
  expect(keys).toHaveLength(2);
  expect(keys[0]).toBeTruthy();
  expect(keys[1]).toBe(keys[0]);
});

test('en móvil la portada pide la versión reducida y no descarga el original', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  const sizes: string[] = [];
  await page.route('**/api/v1/public/stores/conexion-prueba', (route) =>
    route.fulfill({ json: { ...store, coverUrl: '/api/v1/public/media/portada-prueba.jpg' } }),
  );
  await page.route('**/api/v1/public/stores/conexion-prueba/products', (route) =>
    route.fulfill({ json: [] }),
  );
  await page.route('**/api/v1/public/media/portada-prueba.jpg*', (route) => {
    sizes.push(new URL(route.request().url()).searchParams.get('size') ?? 'original');
    return route.fulfill({
      contentType: 'image/svg+xml',
      body: '<svg xmlns="http://www.w3.org/2000/svg" width="640" height="320"/>',
    });
  });
  await page.goto('/tienda/conexion-prueba');
  await expect(page.locator('.store-cover')).toBeVisible();
  await expect.poll(() => sizes.length).toBeGreaterThan(0);
  expect(sizes).toEqual(['sm']);
});
