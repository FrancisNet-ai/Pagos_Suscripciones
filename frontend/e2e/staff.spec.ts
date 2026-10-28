import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { Scenario, setup } from './fixtures';

let scenario: Scenario;
test.beforeAll(async ({ browser, baseURL }) => {
  scenario = await setup(browser, baseURL!);
});
test.afterAll(async () => {
  await scenario?.seller.close();
  await scenario?.admin.close();
});

async function accessible(page: import('@playwright/test').Page) {
  const result = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze();
  expect(result.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical')).toEqual([]);
}

test('el titular invita a un colaborador que solo ve sus secciones y luego le revoca el acceso', async ({ browser, baseURL }, info) => {
  const owner = await scenario.seller.newPage();
  owner.on('dialog', (dialog) => void dialog.accept());
  await owner.goto('/panel/configuracion/equipo');
  await expect(owner.getByTestId('seats')).toContainText('1 de 3 usuarios');
  await accessible(owner);

  const email = `colaborador-${crypto.randomUUID().slice(0, 8)}@example.test`;
  await owner.getByLabel('Nombre', { exact: true }).fill('Ana Apoyo');
  await owner.getByLabel('Correo', { exact: true }).fill(email);
  await owner.getByLabel('Pedidos y atención de compradores').check();
  await owner.getByRole('button', { name: 'Crear invitación' }).click();
  const link = (await owner.getByTestId('invitation-link').textContent())!.trim();
  expect(link).toMatch(/\/invitacion\/[A-Za-z0-9_-]{43}$/);
  await owner.screenshot({ path: info.outputPath('equipo-enlace.png'), fullPage: true });

  const staffContext = await browser.newContext({ baseURL, ...(info.project.use.viewport ? { viewport: info.project.use.viewport } : {}) });
  const staff = await staffContext.newPage();
  await staff.goto(new URL(link).pathname);
  await expect(staff.getByRole('heading', { level: 1 })).toHaveText(`Únete a ${scenario.business.businessName}`);
  await expect(staff.getByLabel('Correo')).toHaveValue(email);
  await accessible(staff);
  await staff.getByLabel('Celular').fill('976543210');
  await staff.getByLabel('Contraseña', { exact: true }).fill('Colaborador-pruebas-2026');
  await staff.getByLabel(/condiciones del servicio/).check();
  await staff.getByLabel(/política de privacidad/).check();
  await staff.getByRole('button', { name: 'Crear mi acceso' }).click();

  await expect(staff).toHaveURL(/\/panel$/);
  await expect(staff.getByRole('heading', { level: 1 })).toHaveText('Hola, Ana.');
  await expect(staff.locator('#panel-nav a', { hasText: 'Pedidos' })).toHaveCount(1);
  await expect(staff.locator('#panel-nav a', { hasText: 'Productos' })).toHaveCount(0);
  await expect(staff.locator('#panel-nav a', { hasText: 'Equipo' })).toHaveCount(0);
  await expect(staff.locator('#panel-nav a', { hasText: 'Mi plan' })).toHaveCount(0);
  await staff.screenshot({ path: info.outputPath('colaborador-inicio.png'), fullPage: true });
  await staff.goto('/panel/ventas');
  await expect(staff.getByRole('heading', { level: 1 })).toBeVisible();
  await expect(staff).toHaveURL(/\/panel\/ventas/);
  // Sin permiso: la ruta lo devuelve al inicio y la API también lo rechaza.
  await staff.goto('/panel/configuracion/cobros');
  await expect(staff).toHaveURL(/\/panel$/);
  expect((await staff.request.get('/api/v1/business/me/payment-methods')).status()).toBe(403);

  await owner.reload();
  await owner.getByTestId(`member-${email}`).getByRole('button', { name: 'Revocar acceso' }).click();
  await expect(owner.getByTestId(`member-${email}`)).toContainText('Acceso revocado');

  await staff.goto('/panel/ventas');
  await expect(staff).toHaveURL(/\/ingresar/);
  await staffContext.close();
  await owner.close();
});
