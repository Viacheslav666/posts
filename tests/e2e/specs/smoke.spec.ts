import { expect, test } from '@playwright/test';

import { expectApiPing } from '../helpers/api';
import { HealthPage } from '../pages/health.page';

/**
 * QA-2 smoke: web `/health` and API `/api/v1/ping`.
 *
 * Local / PR CI: Playwright `webServer` serves the Next.js app (MSW on).
 * API must be reachable at `API_BASE_URL` (bootRun openapi/local or compose `full`).
 * Nightly: both targets come from docker-compose profile `full`.
 */
test.describe('Smoke', () => {
  test('web /health shows status ok', async ({ page }) => {
    const health = new HealthPage(page);
    await health.goto();
    await health.expectOk();
  });

  test('API GET /api/v1/ping returns status ok', async ({ request }) => {
    await expectApiPing(request);
  });

  test('web root responds', async ({ page }) => {
    const response = await page.goto('/');
    expect(response?.ok()).toBeTruthy();
  });
});
