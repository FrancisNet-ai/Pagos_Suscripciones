import { test, expect, Page, TestInfo } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { Scenario, setup, write } from './fixtures';

async function capture(page: Page, info: TestInfo, name: string) {
  await page.evaluate(() => document.fonts.ready);
  if (await page.locator('.fluid-art').count()) {
    await page.evaluate(
      () =>
        new Promise<void>((resolve) => {
          const image = new Image();
          image.onload = () => resolve();
          image.onerror = () => resolve();
          image.src = '/art/nuvyra-fluid.webp';
        }),
    );
  }
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
    `Desbordamiento horizontal en ${name}`,
  ).toBe(true);
  await page.screenshot({ path: info.outputPath(`${name}.png`), fullPage: true });
  const original = page.viewportSize()!;
  for (const width of [360, 768]) {
    await page.setViewportSize({ width, height: 900 });
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
      `${name} a ${width}px`,
    ).toBe(true);
  }
  await page.setViewportSize(original);
}
async function accessible(page: Page) {
  const result = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21aa'])
    .analyze();
  expect(
    result.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical'),
  ).toEqual([]);
}

let scenario: Scenario;
test.beforeAll(async ({ browser, baseURL }) => {
  scenario = await setup(browser, baseURL!);
});
test.afterAll(async () => {
  await scenario?.seller.close();
  await scenario?.admin.close();
});

test('portada, acceso y navegación accesibles en todos los tamaños', async ({ page }, info) => {
  const errors: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Vende, crece y conecta.' })).toBeVisible();
  await expect(page.getByText('Inicio · E2E', { exact: true })).toBeVisible();
  await capture(page, info, '01-portada');
  await accessible(page);
  for (const width of [360, 390, 768, 1440]) {
    await page.setViewportSize({ width, height: 900 });
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
      `Portada a ${width}px`,
    ).toBe(true);
    if (width < 768) {
      const summary = page.getByLabel('Abrir navegación');
      await summary.click();
      await expect(
        page
          .getByRole('navigation', { name: 'Navegación móvil' })
          .getByRole('link', { name: 'Iniciar sesión' }),
      ).toBeVisible();
      await summary.click();
    }
  }
  await page.setViewportSize(info.project.use.viewport!);
  await page.goto('/ingresar');
  await expect(page.getByRole('heading', { name: 'Bienvenido de nuevo' })).toBeVisible();
  await capture(page, info, '02-acceso');
  await accessible(page);
  await page.getByRole('button', { name: 'Ingresar', exact: true }).click();
  await expect(page.getByLabel('Correo electrónico')).toHaveAttribute('aria-invalid', 'true');
  await page.goto('/registro');
  await expect(page.getByRole('heading', { name: 'Crea tu negocio' })).toBeVisible();
  await capture(page, info, '03-registro');
  await page.goto('/recuperar');
  await expect(page.locator('h1')).toBeVisible();
  await capture(page, info, '04-recuperar');
  expect(errors).toEqual([]);
});

test('compra sin cuenta, verificación manual y renovación con el mismo cupo', async ({
  page,
}, info) => {
  const sellerPage = await scenario.seller.newPage();
  await sellerPage.setViewportSize(info.project.use.viewport!);
  try {
    await page.goto(`/tienda/${scenario.business.slug}`);
    await expect(page.getByRole('heading', { name: /Productos que potencian/ })).toBeVisible();
    await capture(page, info, '05-tienda');
    await accessible(page);
    await page.getByRole('button', { name: 'Educación', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Edu Plus · E2E', exact: true })).toBeVisible();
    await expect(
      page.getByRole('heading', { name: 'Stream Premium · E2E', exact: true }),
    ).toHaveCount(0);
    await page.getByRole('button', { name: 'Todos', exact: true }).click();
    await page.getByRole('searchbox', { name: 'Buscar productos' }).fill('sin coincidencias');
    await expect(page.getByText('No encontramos productos con esos filtros.')).toBeVisible();
    await page.getByRole('button', { name: 'Mostrar todos' }).click();
    await page.getByRole('link', { name: 'Comprar Stream Premium · E2E', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Completa tu pedido' })).toBeVisible();
    await capture(page, info, '06-compra');
    await accessible(page);
    await page.getByRole('textbox', { name: 'Tu nombre', exact: true }).fill('María E2E');
    await page.getByRole('textbox', { name: 'Tu celular', exact: true }).fill('911000001');
    await page.getByRole('checkbox').nth(0).check();
    await page.getByRole('checkbox').nth(1).check();
    await page.getByRole('checkbox').nth(2).check();
    await page.getByRole('button', { name: /Continuar al pago/ }).click();
    await expect(page).toHaveURL(/\/pedido\//);
    await expect(page.getByText('Titular E2E', { exact: true })).toBeVisible();
    await capture(page, info, '07-pago');
    await page.getByLabel('Código de operación').fill(`BUY${Date.now()}`);
    await page.getByRole('button', { name: 'Registrar mi pago' }).click();
    await expect(page.getByRole('heading', { name: /Pendiente de verificación/ })).toBeVisible();
    const orders = await (await scenario.seller.request.get('/api/v1/orders')).json();
    await sellerPage.goto(`/panel/ventas/${orders.items[0].id}`);
    await sellerPage.getByRole('checkbox').check();
    await capture(sellerPage, info, '08-verificar-pago');
    await sellerPage.getByRole('button', { name: 'Aprobar pago' }).click();
    await expect(sellerPage.getByRole('term').filter({ hasText: /^Verificado$/ })).toBeVisible();
    const subscriptions = await (await scenario.seller.request.get('/api/v1/subscriptions')).json();
    const sub = subscriptions.items[0];
    await write(scenario.seller.request, 'post', `/api/v1/subscriptions/${sub.id}/activate`, {
      startAt: new Date(Date.now() - 28 * 86400000).toISOString(),
    });
    let renewalUrl = '';
    await expect
      .poll(
        async () => {
          const list = await (
            await scenario.seller.request.get('/api/v1/renewals?bucket=UPCOMING')
          ).json();
          renewalUrl =
            list.items.find((r: { subscriptionId: string }) => r.subscriptionId === sub.id)
              ?.renewalUrl ?? '';
          return renewalUrl;
        },
        { timeout: 30_000 },
      )
      .not.toBe('');
    await page.goto(new URL(renewalUrl).pathname);
    await expect(page.getByRole('heading', { name: 'Hola, María.' })).toBeVisible();
    await capture(page, info, '09-renovar');
    await page.getByRole('button', { name: 'Sí, renovar' }).click();
    await expect(page.getByText(/se confirmará cuando/)).toBeVisible();
    await page.getByRole('button', { name: /Continuar al pago/ }).click();
    await page.getByLabel('Código de operación').fill(`REN${Date.now()}`);
    await page.getByRole('button', { name: 'Registrar mi pago' }).click();
    await expect(page.getByRole('heading', { name: /Pendiente de verificación/ })).toBeVisible();
    const renewedOrders = await (await scenario.seller.request.get('/api/v1/orders')).json();
    await sellerPage.goto(`/panel/ventas/${renewedOrders.items[0].id}`);
    await sellerPage.getByRole('checkbox').check();
    await sellerPage.getByRole('button', { name: 'Aprobar pago' }).click();
    await expect(sellerPage.getByRole('term').filter({ hasText: /^Verificado$/ })).toBeVisible();
    await page.goto(new URL(renewalUrl).pathname);
    await expect(page.getByRole('heading', { name: '¡Tu suscripción se renovó!' })).toBeVisible();
    await capture(page, info, '10-renovacion-confirmada');
    const product = await (
      await page.request.get(
        `/api/v1/public/stores/${scenario.business.slug}/products/${scenario.products[0].id}`,
      )
    ).json();
    expect(product.availableSlots).toBe(3);
    await sellerPage.goto('/panel');
    await expect(sellerPage.getByRole('heading', { name: 'Hola, Carlos.' })).toBeVisible();
    await expect(sellerPage.locator('.dashboard-kpis')).toContainText('S/ 30.00');
    await capture(sellerPage, info, '11-dashboard');
    await accessible(sellerPage);
    if (info.project.name === 'mobile') {
      const menu = sellerPage.getByRole('button', { name: 'Menú', exact: true });
      await menu.click();
      await expect(sellerPage.getByRole('dialog', { name: 'Navegación del panel' })).toBeVisible();
      await sellerPage.keyboard.press('Escape');
      await expect(menu).toBeFocused();
    }
  } finally {
    await sellerPage.close();
  }
});

test('paneles, facturación y herramientas de las fases 6 y 7', async ({ browser }, info) => {
  const sellerPage = await scenario.seller.newPage();
  const adminPage = await scenario.admin.newPage();
  try {
    await sellerPage.setViewportSize(info.project.use.viewport!);
    await adminPage.setViewportSize(info.project.use.viewport!);
    for (const [route, name] of [
      ['/panel/productos', '12-productos'],
      ['/panel/productos/nuevo', '13-producto-nuevo'],
      ['/panel/clientes', '14-clientes'],
      ['/panel/suscripciones', '15-suscripciones'],
      ['/panel/renovaciones', '16-renovaciones'],
      ['/panel/reportes', '17-reportes'],
      ['/panel/plan', '18-facturacion'],
      ['/panel/configuracion/negocio', '19-configuracion'],
      ['/panel/configuracion/whatsapp', '20-whatsapp'],
      ['/panel/clientes/importar', '21-importacion'],
    ]) {
      await sellerPage.goto(route);
      await expect(sellerPage.locator('h1')).toBeVisible();
      await expect(sellerPage.locator('[aria-busy=true]')).toHaveCount(0);
      await capture(sellerPage, info, name);
    }
    await sellerPage.locator('#import-file').setInputFiles({
      name: 'clientes-e2e.csv',
      mimeType: 'text/csv',
      buffer: Buffer.from('nombre,celular\nAna Importada E2E,911000123\n'),
    });
    await expect(sellerPage.getByText('Ana Importada E2E', { exact: true })).toBeVisible();
    await capture(sellerPage, info, '22-importacion-previa');
    await sellerPage.getByRole('button', { name: /Confirmar importación/ }).click();
    await expect(sellerPage.getByRole('button', { name: 'Importar otro archivo' })).toBeVisible();
    for (const [route, name] of [
      ['/admin', '23-admin'],
      ['/admin/planes', '24-admin-planes'],
      ['/admin/negocios', '25-admin-negocios'],
      ['/admin/pagos', '26-admin-pagos'],
      ['/admin/configuracion', '27-admin-configuracion'],
      ['/admin/auditoria', '28-admin-auditoria'],
    ]) {
      await adminPage.goto(route);
      await expect(adminPage.locator('h1')).toBeVisible();
      await expect(adminPage.locator('[aria-busy=true]')).toHaveCount(0);
      await capture(adminPage, info, name);
    }
    const pending = await browser.newContext({
      baseURL: info.project.use.baseURL ?? process.env['E2E_BASE_URL'] ?? 'http://localhost:4200',
      viewport: info.project.use.viewport!,
    });
    try {
      await write(pending.request, 'post', '/api/v1/auth/register', {
        fullName: 'Vendedor Pendiente E2E',
        email: `pending-${crypto.randomUUID()}@example.test`,
        password: 'Nuvyra-solo-pruebas-2026',
        phone: '987654322',
        businessName: 'Plan Pendiente E2E',
        acceptTerms: true,
        acceptPrivacy: true,
        planId: scenario.plan.id,
      });
      const pendingPage = await pending.newPage();
      await pendingPage.goto('/panel/plan');
      await expect(pendingPage.getByText('Sin plan vigente', { exact: true })).toBeVisible();
      await pendingPage.getByLabel('Código de operación').fill(`PLAN${Date.now()}`);
      await pendingPage.getByRole('radio', { name: 'Yape', exact: true }).check();
      await pendingPage.getByRole('button', { name: 'Reportar pago', exact: true }).click();
      await expect(pendingPage.getByText('Pago en revisión', { exact: true })).toBeVisible();
      await capture(pendingPage, info, '29-plan-en-revision');
      const billing = await (await pending.request.get('/api/v1/billing')).json();
      const payments = await (
        await scenario.admin.request.get('/api/v1/admin/saas-payments?status=PENDING_VERIFICATION')
      ).json();
      const payment = payments.items.find(
        (p: { invoiceId: string }) => p.invoiceId === billing.openInvoice.id,
      );
      await write(
        scenario.admin.request,
        'post',
        `/api/v1/admin/saas-payments/${payment.id}/verify`,
      );
      await pendingPage.reload();
      await expect(pendingPage.getByText('Plan vigente', { exact: true })).toBeVisible();
    } finally {
      await pending.close();
    }
  } finally {
    await sellerPage.close();
    await adminPage.close();
  }
});
