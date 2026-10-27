import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { Scenario, formPost, setup, write } from './fixtures';

let scenario: Scenario;
test.beforeAll(async ({ browser, baseURL }) => {
  scenario = await setup(browser, baseURL!);
});
test.afterAll(async () => {
  await scenario?.seller.close();
  await scenario?.admin.close();
});

test('un pago reportado aparece en Pendientes y se resuelve al aprobarlo en el pedido', async ({
  browser,
  baseURL,
}, info) => {
  const buyer = await browser.newContext({ baseURL });
  const order = await write(
    buyer.request,
    'post',
    `/api/v1/public/stores/${scenario.business.slug}/orders`,
    {
      productId: scenario.products[2].id,
      customerName: 'Rosa Pendientes',
      phone: '976000111',
      paymentMethod: 'YAPE',
      acceptTerms: true,
      acceptDataProcessing: true,
    },
  );
  await formPost(buyer.request, `/api/v1/public/orders/${order.publicToken}/payment-report`, {
    transactionReference: `P${Date.now()}`,
  });
  await buyer.close();

  const owner = await scenario.seller.newPage();
  await owner.goto('/panel/pendientes');
  const task = owner.getByTestId('task-VERIFY_PAYMENT').filter({ hasText: 'Rosa Pendientes' });
  await expect(task).toBeVisible();
  await expect(owner.locator('#panel-nav .nav-count')).not.toHaveCount(0);
  const result = await new AxeBuilder({ page: owner })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21aa'])
    .analyze();
  expect(
    result.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical'),
  ).toEqual([]);
  await owner.screenshot({ path: info.outputPath('pendientes.png'), fullPage: true });

  await task.getByRole('link', { name: 'Revisar pago' }).click();
  await expect(owner).toHaveURL(/\/panel\/ventas\/[0-9a-f-]{36}$/);
  await owner.getByLabel(/Confirmo que recibí/).check();
  await owner.getByRole('button', { name: /Aprobar pago/ }).click();
  await expect(owner.getByRole('button', { name: /Aprobar pago/ })).toHaveCount(0);

  await owner.goto('/panel/pendientes');
  await expect(
    owner.getByTestId('task-VERIFY_PAYMENT').filter({ hasText: 'Rosa Pendientes' }),
  ).toHaveCount(0);
  await owner.getByRole('link', { name: 'Ver historial de pendientes' }).click();
  await expect(owner.getByRole('heading', { level: 1 })).toHaveText('Historial de pendientes');
  const closed = owner
    .getByTestId('resolved-VERIFY_PAYMENT')
    .filter({ hasText: 'Rosa Pendientes' });
  await expect(closed).toContainText('Pago verificado');
  await expect(closed).toContainText('Acción realizada por Carlos E2E');
  await expect(closed.getByRole('button')).toHaveCount(0);
  const historyAxe = await new AxeBuilder({ page: owner })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21aa'])
    .analyze();
  expect(
    historyAxe.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical'),
  ).toEqual([]);
  for (const width of [360, 768]) {
    await owner.setViewportSize({ width, height: 900 });
    expect(await owner.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    );
  }
  await owner.setViewportSize(info.project.use.viewport!);
  await owner.screenshot({ path: info.outputPath('historial-pendientes.png'), fullPage: true });
  await owner.close();
});
