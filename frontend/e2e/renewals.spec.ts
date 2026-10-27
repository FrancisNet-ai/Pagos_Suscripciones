import { test, expect, APIRequestContext } from '@playwright/test';

// Datos exclusivos de prueba. El backend y PostgreSQL deben ser instancias locales desechables.
const password = 'clave-e2e-solo-local-123';

async function write(api: APIRequestContext, method: 'post' | 'put' | 'patch', url: string, data: unknown) {
  await api.get('/api/v1/auth/csrf');
  const csrf = (await api.storageState()).cookies.find((c) => c.name === 'XSRF-TOKEN')!.value;
  const response = await api[method](url, { data, headers: { 'X-XSRF-TOKEN': decodeURIComponent(csrf) } });
  expect(response.ok(), `${method} ${url}: ${await response.text()}`).toBeTruthy();
  return response.json();
}

test('compra y renovación reales, verificación manual, mismo cupo e historial', async ({ browser, page, baseURL }, testInfo) => {
  const seller = await browser.newContext({ baseURL });
  const api = seller.request;
  const email = `fase5-${crypto.randomUUID()}@example.test`;
  try {
    await write(api, 'post', '/api/v1/auth/register', {
      fullName: 'Vendedor E2E', email, password, phone: '987654321', businessName: 'Tienda E2E', acceptTerms: true, acceptPrivacy: true,
    });
    await write(api, 'put', '/api/v1/business/me', { businessName: 'Tienda E2E', timeZone: 'America/Lima', whatsappNumber: '912345678' });
    await write(api, 'put', '/api/v1/business/me/payment-methods/yape', { holderName: 'Titular E2E', phone: '911222333', enabled: true });
    const product = await write(api, 'post', '/api/v1/products', {
      name: 'Servicio E2E', productType: 'SUBSCRIPTION', price: 15, durationDays: 30, capacityEnabled: true, capacity: 1, available: true,
    });
    await write(api, 'patch', `/api/v1/products/${product.id}/status`, { publicationStatus: 'PUBLISHED' });
    const business = await (await api.get('/api/v1/business/me')).json();

    await page.goto(`/tienda/${business.slug}`);
    await page.getByRole('link', { name: /comprar/i }).click();
    await page.getByRole('textbox', { name: 'Tu nombre', exact: true }).fill('María E2E');
    await page.getByRole('textbox', { name: 'Tu celular', exact: true }).fill('911000001');
    await page.getByRole('radio', { name: 'Yape', exact: true }).check();
    await page.getByRole('checkbox').nth(0).check();
    await page.getByRole('checkbox').nth(1).check();
    await page.getByRole('checkbox').nth(2).check();
    await page.getByRole('button', { name: /continuar|crear pedido/i }).click();
    await expect(page).toHaveURL(/\/pedido\//);
    await expect(page.getByText('Titular E2E', { exact: true })).toBeVisible();
    await page.getByLabel('Código de operación').fill(`BUY${Date.now()}`);
    await page.getByRole('button', { name: 'Registrar mi pago' }).click();
    await expect(page.getByRole('heading', { name: /verificación/i })).toBeVisible();

    const sellerPage = await seller.newPage();
    const orders = await (await api.get('/api/v1/orders')).json();
    const firstOrder = orders.items[0];
    await sellerPage.goto(`/panel/ventas/${firstOrder.id}`);
    await sellerPage.getByRole('checkbox').check();
    await sellerPage.getByRole('button', { name: 'Aprobar pago' }).click();
    await expect(sellerPage.getByRole('term').filter({ hasText: /^Verificado$/ })).toBeVisible();

    const subscriptions = await (await api.get('/api/v1/subscriptions')).json();
    const sub = subscriptions.items[0];
    // Inicio real de una entrega realizada hace 28 días: entra en la ventana de recordatorio.
    const startAt = new Date(Date.now() - 28 * 86_400_000).toISOString();
    await write(api, 'post', `/api/v1/subscriptions/${sub.id}/activate`, { startAt });
    let link = '';
    await expect.poll(async () => {
      const list = await (await api.get('/api/v1/renewals?bucket=UPCOMING')).json();
      link = list.items.find((r: { subscriptionId: string }) => r.subscriptionId === sub.id)?.renewalUrl ?? '';
      return link;
    }, { timeout: 30_000 }).not.toBe('');

    await sellerPage.goto('/panel/renovaciones');
    await expect(sellerPage.getByRole('link', { name: 'Abrir WhatsApp' })).toBeVisible();
    await expect(sellerPage.getByRole('button', { name: 'Ya lo envié' })).toBeDisabled();
    await page.goto(new URL(link).pathname);
    await expect(page.getByRole('heading', { name: 'Hola, María.' })).toBeVisible();
    await page.getByRole('button', { name: 'Sí, renovar' }).click();
    await expect(page.getByText(/se confirmará cuando/)).toBeVisible();
    await page.getByRole('button', { name: /Continuar al pago/ }).click();
    await expect(page).toHaveURL(/\/pedido\//);
    await page.getByLabel('Código de operación').fill(`REN${Date.now()}`);
    await page.getByRole('button', { name: 'Registrar mi pago' }).click();
    await expect(page.getByRole('heading', { name: /verificación/i })).toBeVisible();

    const renewalOrders = await (await api.get('/api/v1/orders')).json();
    await sellerPage.goto(`/panel/ventas/${renewalOrders.items[0].id}`);
    await sellerPage.getByRole('checkbox').check();
    await sellerPage.getByRole('button', { name: 'Aprobar pago' }).click();
    await expect(sellerPage.getByRole('term').filter({ hasText: /^Verificado$/ })).toBeVisible();
    await page.goto(new URL(link).pathname);
    await expect(page.getByText(/Tu renovación fue confirmada/)).toBeVisible();
    await expect.poll(async () => (await (await api.get(`/api/v1/subscriptions/${sub.id}/renewals`)).json()).totalItems).toBe(1);
    const updated = await (await api.get(`/api/v1/subscriptions/${sub.id}`)).json();
    expect(updated.capacityStatus).toBe('OCCUPIED');
    expect(updated.status).toBe('ACTIVE');
    const publicProducts = await (await page.request.get(`/api/v1/public/stores/${business.slug}/products`)).json();
    expect(publicProducts[0].soldOut).toBe(true);
    await sellerPage.goto(`/panel/clientes/${sub.customerId}`);
    await sellerPage.getByRole('button', { name: 'Historial de renovaciones' }).click();
    await expect(sellerPage.getByText(/Vencimiento anterior/)).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.screenshot({ path: testInfo.outputPath('renovacion-confirmada.png'), fullPage: true });
  } finally { await seller.close(); }
});
