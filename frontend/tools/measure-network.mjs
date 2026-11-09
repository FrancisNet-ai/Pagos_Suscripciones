// Medición local del build de producción. La API de esta muestra solo devuelve sesión invitada y planes vacíos.
// No mide la base de datos ni se conecta a producción. Requiere npm ci y Playwright Chromium instalado.
import { createServer } from 'node:http';
import { readFile, stat } from 'node:fs/promises';
import { resolve, extname, sep } from 'node:path';
import { gzipSync } from 'node:zlib';
import { chromium } from '@playwright/test';

const args = process.argv.slice(2);
const root = resolve(args.find((arg) => !arg.startsWith('--')) ?? 'dist/frontend/browser');
const compressed = args.includes('--gzip');
const types = {
  '.html': 'text/html',
  '.js': 'application/javascript',
  '.css': 'text/css',
  '.svg': 'image/svg+xml',
  '.webp': 'image/webp',
  '.woff2': 'font/woff2',
  '.ico': 'image/x-icon',
};
await stat(resolve(root, 'index.html'));
const server = createServer(async (req, res) => {
  const path = new URL(req.url, 'http://localhost').pathname;
  if (req.method !== 'GET') {
    res.writeHead(405);
    res.end();
    return;
  }
  if (path.startsWith('/api/')) {
    setTimeout(() => {
      if (path === '/api/v1/auth/csrf') {
        res.writeHead(204);
        res.end();
        return;
      }
      res.writeHead(path === '/api/v1/public/saas-plans' ? 200 : 401, {
        'Content-Type': 'application/json',
        'Cache-Control': 'no-store',
      });
      res.end(path === '/api/v1/public/saas-plans' ? '[]' : '{}');
    }, 500);
    return;
  }
  let file = resolve(root, `.${decodeURIComponent(path)}`);
  if (file !== root && !file.startsWith(root + sep)) {
    res.writeHead(404);
    res.end();
    return;
  }
  if (!extname(path)) file = resolve(root, 'index.html');
  try {
    let bytes = await readFile(file);
    const type = types[extname(file)] ?? 'application/octet-stream';
    const gzip = compressed && /text|javascript|json|svg/.test(type) && bytes.length >= 1024;
    if (gzip) bytes = gzipSync(bytes);
    res.writeHead(200, {
      'Content-Type': type,
      'Content-Length': bytes.length,
      ...(gzip ? { 'Content-Encoding': 'gzip', Vary: 'Accept-Encoding' } : {}),
    });
    res.end(bytes);
  } catch {
    res.writeHead(404);
    res.end();
  }
});
await new Promise((done) => server.listen(0, '127.0.0.1', done));
const url = `http://127.0.0.1:${server.address().port}`;
const browser = await chromium.launch();
const samples = [];
try {
  for (let run = 0; run < 3; run++) {
    const context = await browser.newContext({ viewport: { width: 390, height: 844 } });
    const page = await context.newPage();
    const cdp = await context.newCDPSession(page);
    await cdp.send('Network.enable');
    await cdp.send('Network.setCacheDisabled', { cacheDisabled: true });
    await cdp.send('Network.emulateNetworkConditions', {
      offline: false,
      latency: 300,
      downloadThroughput: 64 * 1024,
      uploadThroughput: 32 * 1024,
    });
    await cdp.send('Emulation.setCPUThrottlingRate', { rate: 4 });
    await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 90_000 });
    await page
      .getByRole('heading', { name: 'Vende, crece y conecta.' })
      .waitFor({ timeout: 90_000 });
    const contentReadyMs = await page.evaluate(() => performance.now());
    await page.evaluate(() => document.fonts.ready);
    await page.waitForLoadState('networkidle');
    samples.push(
      await page.evaluate((contentReadyMs) => {
        const resources = performance.getEntriesByType('resource');
        const navigation = performance.getEntriesByType('navigation')[0];
        return {
          contentReadyMs: Math.round(contentReadyMs),
          firstContentfulPaintMs: Math.round(
            performance.getEntriesByName('first-contentful-paint')[0]?.startTime ?? 0,
          ),
          transferredBytes:
            navigation.transferSize + resources.reduce((n, r) => n + r.transferSize, 0),
          requests: resources.length + 1,
          authRequests: resources
            .filter((r) => r.name.includes('/api/v1/auth/'))
            .map((r) => ({
              path: new URL(r.name).pathname,
              startMs: Math.round(r.startTime),
              endMs: Math.round(r.responseEnd),
            })),
        };
      }, contentReadyMs),
    );
    await context.close();
  }
  const median = (field) => samples.map((sample) => sample[field]).sort((a, b) => a - b)[1];
  console.log(
    JSON.stringify(
      {
        profile: '390x844, 64 KiB/s, 300 ms latency, CPU 4x, cold cache, simulated API 500 ms',
        gzip: compressed,
        samples,
        median: {
          contentReadyMs: median('contentReadyMs'),
          transferredBytes: median('transferredBytes'),
          firstContentfulPaintMs: median('firstContentfulPaintMs'),
        },
      },
      null,
      2,
    ),
  );
} finally {
  await browser.close();
  server.closeAllConnections();
  server.close();
}
