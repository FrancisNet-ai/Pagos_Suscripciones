import { expect, APIRequestContext, Browser } from '@playwright/test';

export async function write(
  api: APIRequestContext,
  method: 'post' | 'put' | 'patch',
  path: string,
  data?: unknown,
) {
  await api.get('/api/v1/auth/csrf');
  const csrf = (await api.storageState()).cookies.find((c) => c.name === 'XSRF-TOKEN');
  expect(csrf, 'Cookie CSRF').toBeDefined();
  const response = await api[method](path, {
    data,
    headers: { 'X-XSRF-TOKEN': decodeURIComponent(csrf!.value) },
  });
  expect(response.ok(), `${method} ${path}: ${await response.text()}`).toBeTruthy();
  const body = await response.text();
  return body ? JSON.parse(body) : null;
}

export async function formPost(
  api: APIRequestContext,
  path: string,
  multipart: Record<string, string>,
) {
  await api.get('/api/v1/auth/csrf');
  const csrf = (await api.storageState()).cookies.find((c) => c.name === 'XSRF-TOKEN')!;
  const response = await api.post(path, {
    multipart,
    headers: { 'X-XSRF-TOKEN': decodeURIComponent(csrf.value) },
  });
  expect(response.ok(), `${path}: ${await response.text()}`).toBeTruthy();
  return response.json();
}

export async function setup(browser: Browser, baseURL: string) {
  if (!['localhost', '127.0.0.1'].includes(new URL(baseURL).hostname))
    throw new Error('Las pruebas crean datos: requieren un entorno local desechable.');
  const adminEmail = process.env['E2E_ADMIN_EMAIL'];
  const adminPassword = process.env['E2E_ADMIN_PASSWORD'];
  if (!adminEmail || !adminPassword)
    throw new Error(
      'Define E2E_ADMIN_EMAIL y E2E_ADMIN_PASSWORD del administrador LOCAL de pruebas.',
    );
  const admin = await browser.newContext({ baseURL });
  await write(admin.request, 'post', '/api/v1/auth/login', {
    email: adminEmail,
    password: adminPassword,
  });
  let plans = await (await admin.request.get('/api/v1/admin/plans')).json();
  for (const [index, name, price, billingPeriod] of [
    [0, 'Inicio · E2E', 29, 'MONTHLY'],
    [1, 'Crece · E2E', 79, 'QUARTERLY'],
    [2, 'Expande · E2E', 249, 'YEARLY'],
  ] as const) {
    if (!plans.some((p: { name: string }) => p.name === name)) {
      await write(admin.request, 'post', '/api/v1/admin/plans', {
        name,
        price,
        billingPeriod,
        description: 'Plan exclusivo para pruebas del rediseño.',
        productLimit: 20,
        customerLimit: 100,
        adminUserLimit: 1,
        featureReports: true,
        featureReminders: true,
        featureBranding: true,
        active: true,
        sortOrder: index,
      });
    }
  }
  plans = await (await admin.request.get('/api/v1/admin/plans')).json();
  const plan = plans.find((p: { name: string }) => p.name === 'Inicio · E2E');
  await write(admin.request, 'put', '/api/v1/admin/settings/payment-methods/YAPE', {
    holderName: 'Nuvyra Pruebas E2E',
    phone: '911222333',
    enabled: true,
  });
  const seller = await browser.newContext({ baseURL });
  const email = `redesign-${crypto.randomUUID()}@example.test`;
  const password = 'Nuvyra-solo-pruebas-2026';
  await write(seller.request, 'post', '/api/v1/auth/register', {
    fullName: 'Carlos E2E',
    email,
    password,
    phone: '987654321',
    businessName: 'Norte Digital · E2E',
    acceptTerms: true,
    acceptPrivacy: true,
    planId: plan.id,
  });
  const billing = await (await seller.request.get('/api/v1/billing')).json();
  await formPost(seller.request, `/api/v1/billing/invoices/${billing.openInvoice.id}/payments`, {
    method: 'YAPE',
    transactionReference: `SAAS${Date.now()}`,
  });
  const pending = await (
    await admin.request.get('/api/v1/admin/saas-payments?status=PENDING_VERIFICATION')
  ).json();
  const payment = pending.items.find(
    (p: { invoiceId: string }) => p.invoiceId === billing.openInvoice.id,
  );
  expect(payment, 'Pago SaaS del vendedor de prueba').toBeDefined();
  await write(admin.request, 'post', `/api/v1/admin/saas-payments/${payment.id}/verify`);
  await write(seller.request, 'put', '/api/v1/business/me', {
    businessName: 'Norte Digital · E2E',
    description:
      'Suscripciones y herramientas digitales para aprender, crear y disfrutar. Datos exclusivos de prueba.',
    timeZone: 'America/Lima',
    whatsappNumber: '912345678',
    primaryColor: '#1A3D63',
  });
  await write(seller.request, 'put', '/api/v1/business/me/payment-methods/yape', {
    holderName: 'Titular E2E',
    phone: '911222334',
    enabled: true,
  });
  const products = [];
  for (const [name, category, price, productType] of [
    ['Stream Premium · E2E', 'Entretenimiento', 15, 'SUBSCRIPTION'],
    ['Edu Plus · E2E', 'Educación', 19, 'SUBSCRIPTION'],
    ['Herramientas Pro · E2E', 'Software', 39, 'ONE_TIME'],
    ['Diseño Digital · E2E', 'Diseño', 49, 'ONE_TIME'],
  ] as const) {
    const product = await write(seller.request, 'post', '/api/v1/products', {
      name,
      category,
      description: 'Producto exclusivo del entorno de pruebas del rediseño.',
      price,
      productType,
      durationDays: productType === 'SUBSCRIPTION' ? 30 : null,
      capacityEnabled: productType === 'SUBSCRIPTION',
      capacity: productType === 'SUBSCRIPTION' ? 4 : null,
      available: true,
    });
    await write(seller.request, 'patch', `/api/v1/products/${product.id}/status`, {
      publicationStatus: 'PUBLISHED',
    });
    products.push(product);
  }
  const business = await (await seller.request.get('/api/v1/business/me')).json();
  return { admin, seller, plan, products, business, email, password };
}
export type Scenario = Awaited<ReturnType<typeof setup>>;
