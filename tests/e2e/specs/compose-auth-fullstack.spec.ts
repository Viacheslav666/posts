import { expect, test } from '@playwright/test';

import { RegisterPage } from '../pages/register.page';

/**
 * Compose profile `full` via gateway :3000 — real API (no MSW), Mailpit verification, browser CSRF cookies.
 */
const enabled = process.env.COMPOSE_AUTH_E2E === '1';

test.describe('Compose auth full stack', () => {
  test.skip(!enabled, 'Set COMPOSE_AUTH_E2E=1 with PLAYWRIGHT_SKIP_WEBSERVER=1 and gateway on :3000');

  test('register → verify email → login through :3000', async ({ page, request }) => {
    const mailpitApi = (process.env.MAILPIT_API_URL ?? 'http://127.0.0.1:8025').replace(/\/$/, '');
    const email = `e2e-auth-${Date.now()}@example.com`;
    const password = 'SmokeTest1!SmokeTest1!';
    const name = 'Compose E2E';

    const register = new RegisterPage(page);
    await register.goto();
    await register.expectFormVisible();
    await register.fillCredentials({ name, email, password });
    await register.submit();
    await register.expectVerifyEmail(email);

    let messageId = '';
    for (let i = 0; i < 45; i++) {
      const search = await request.get(`${mailpitApi}/api/v1/search?query=${encodeURIComponent(email)}`);
      expect(search.ok()).toBeTruthy();
      const body = (await search.json()) as { messages?: { ID: string }[] };
      if (body.messages?.length) {
        messageId = body.messages[0].ID;
        break;
      }
      await page.waitForTimeout(2000);
    }
    expect(messageId, 'verification email in Mailpit').not.toBe('');

    const message = await request.get(`${mailpitApi}/api/v1/message/${messageId}`);
    expect(message.ok()).toBeTruthy();
    const mail = (await message.json()) as { Text?: string; HTML?: string };
    const text = mail.Text ?? mail.HTML ?? '';
    const tokenMatch = text.match(/token=([A-Za-z0-9_-]+)/);
    expect(tokenMatch, 'token in verification mail').toBeTruthy();
    const token = tokenMatch![1];

    await page.goto(`/verify-email?email=${encodeURIComponent(email)}&token=${token}`);
    await expect(page.getByRole('heading', { name: 'Подтверждение email' })).toBeVisible();
    await page.getByRole('button', { name: 'Подтвердить' }).click();
    await page.waitForURL(/\/login/, { timeout: 30_000 });

    await page.getByLabel('Эл. почта').fill(email);
    await page.getByLabel('Пароль').fill(password);
    await page.getByTestId('login-submit').click();

    await page.waitForURL(/\/app\/[0-9a-f-]+/i, { timeout: 30_000 });
  });
});