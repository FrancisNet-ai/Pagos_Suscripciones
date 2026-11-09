import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { formPost, setup, write } from './fixtures';

test('el vendedor distingue cobros de pendientes y consulta el gráfico en móvil y escritorio', async ({
  browser,
  page,
  context,
  baseURL,
}, info) => {
  const scenario = await setup(browser, baseURL!);
  const buyer = await browser.newContext({ baseURL });
  try {
    await context.addCookies(await scenario.seller.cookies());
    await page.goto('/panel');
    await expect(page.getByText('Aún no hay cobros verificados en este periodo.')).toBeVisible();

    for (let i = 0; i < 3; i++) {
      const order = await write(
        buyer.request,
        'post',
        `/api/v1/public/stores/${scenario.business.slug}/orders`,
        {
          productId: scenario.products[0].id,
          customerName: 'Comprador del panel · E2E',
          phone: `91188000${i}`,
          paymentMethod: 'YAPE',
          acceptTerms: true,
          acceptDataProcessing: true,
          acceptRenewalReminders: false,
          acceptOrderUpdates: false,
        },
      );
      await formPost(buyer.request, `/api/v1/public/orders/${order.publicToken}/payment-report`, {
        transactionReference: `PANEL${Date.now()}${i}`,
      });
      if (i < 2) {
        const list = await (
          await scenario.seller.request.get('/api/v1/orders?filter=TO_VERIFY')
        ).json();
        const pending = list.items.find(
          (o: { orderNumber: string }) => o.orderNumber === order.orderNumber,
        );
        const detail = await (
          await scenario.seller.request.get(`/api/v1/orders/${pending.id}`)
        ).json();
        await write(
          scenario.seller.request,
          'post',
          `/api/v1/payments/${detail.payments[0].id}/verify`,
        );
      }
    }

    let requests = 0;
    page.on('request', (r) => {
      if (new URL(r.url()).pathname === '/api/v1/reports/dashboard') requests++;
    });
    await page.reload();
    await expect(page.locator('.metric-income')).toContainText('30.00');
    await expect(page.locator('.balance-today')).toContainText('30.00');
    await expect(page.locator('.balance-pending')).toContainText('15.00');
    await expect(page.locator('.revenue-total')).toContainText('30.00');
    const count = requests;
    await page.getByRole('button', { name: '7 días', exact: true }).click();
    await expect(page.getByRole('button', { name: '7 días', exact: true })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    await expect(page.locator('.revenue-total')).toContainText('30.00');
    const slider = page.getByRole('slider', { name: 'Elegir día de ingresos' });
    await slider.focus();
    await slider.press('Home');
    await expect(slider).toHaveValue('0');
    await slider.press('End');
    await expect(slider).toHaveValue('6');
    await expect(page.locator('.revenue-selection')).toContainText('30.00');
    if (info.project.name === 'mobile') {
      const rect = (await page.locator('app-revenue-chart svg').boundingBox())!;
      await page.touchscreen.tap(rect.x + rect.width / 2, rect.y + rect.height / 2);
      await expect(slider).toHaveValue('3');
      await slider.focus();
      await slider.press('End');
    }
    await page.getByText('Ver datos del gráfico', { exact: true }).click();
    await expect(page.locator('app-revenue-chart tbody tr')).toHaveCount(7);
    await page.getByRole('button', { name: '30 días', exact: true }).click();
    await expect(page.locator('app-revenue-chart tbody tr')).toHaveCount(30);
    expect(requests).toBe(count);
    await page.getByText('Ver datos del gráfico', { exact: true }).click();

    const accessibility = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21aa'])
      .analyze();
    expect(
      accessibility.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical'),
    ).toEqual([]);
    const viewport = page.viewportSize()!;
    for (const width of [360, 390, 768, 1440]) {
      await page.setViewportSize({ width, height: 1000 });
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
        true,
      );
    }
    await page.setViewportSize(viewport);
    if (info.project.name === 'mobile') {
      const balances = (await page.locator('.income-balances').boundingBox())!;
      const graph = (await page.locator('.income-main').boundingBox())!;
      expect(balances.y).toBeLessThan(graph.y);
    }
    await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'instant' }));
    await page.screenshot({ path: info.outputPath('panel-ingresos.png'), fullPage: true });
  } finally {
    await buyer.close();
    await scenario.seller.close();
    await scenario.admin.close();
  }
});
