import { chromium, devices } from '@playwright/test';
import { mkdir } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const outDir = path.resolve(__dirname, '../../../docs/fe-2');
const baseURL = process.env.PLAYWRIGHT_BASE_URL ?? 'http://127.0.0.1:3000';

const pages = [
  { name: 'landing', path: '/' },
  { name: 'pricing', path: '/pricing' },
  { name: 'register', path: '/register' },
  { name: 'login', path: '/login' },
  { name: 'two-factor', path: '/two-factor?challengeId=chal_demo_2fa' },
];

await mkdir(outDir, { recursive: true });

const browser = await chromium.launch();

async function capture(viewportName, contextOptions) {
  const context = await browser.newContext(contextOptions);
  const page = await context.newPage();
  for (const item of pages) {
    await page.goto(new URL(item.path, baseURL).toString(), { waitUntil: 'networkidle' });
    await page.waitForTimeout(400);
    const file = path.join(outDir, `${item.name}-${viewportName}.png`);
    await page.screenshot({ path: file, fullPage: true });
    console.log(`wrote ${file}`);
  }
  await context.close();
}

await capture('desktop', { viewport: { width: 1440, height: 900 } });
await capture('mobile', { ...devices['iPhone 12'], viewport: { width: 390, height: 844 } });

await browser.close();
