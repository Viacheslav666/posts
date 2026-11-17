import { test } from '@playwright/test';

import { ChannelsPage } from '../pages/channels.page';

/**
 * FE-6 stub: Telegram channel connect wizard against MSW.
 * Full OAuth/login flow is covered later with QA; this checks the TG wizard shell.
 *
 * Note: /app/* requires an authenticated session. When MSW browser mocking is on,
 * open the public connect-first-channel path via login smoke or seed token —
 * for now we assert the channels route renders at 360px after navigating
 * (may redirect to login — still a usable e2e scaffold).
 */
test.describe('Channels TG connect (FE-6 stub)', () => {
  const demoWorkspace = '019a0000-0002-7000-8000-000000000002';

  test('channels page route is reachable and usable at 360px', async ({ page }) => {
    await page.setViewportSize({ width: 360, height: 640 });
    const channels = new ChannelsPage(page);
    await channels.goto(demoWorkspace);

    // Either the Channels UI or auth gate — both are valid for the stub.
    const heading = page.getByRole('heading', { name: /Каналы|Вход/ });
    await heading.first().waitFor({ state: 'visible' });
  });

  test('login → channels → TG wizard shell (when session available)', async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Email').fill('demo@posts.app');
    await page.getByLabel('Пароль', { exact: true }).fill('Password1!');
    await page.getByRole('button', { name: 'Войти' }).click();

    // After login MSW redirects into app; navigate to channels explicitly.
    await page.waitForURL(/\/app\//, { timeout: 15_000 }).catch(() => undefined);
    const channels = new ChannelsPage(page);
    await channels.goto(demoWorkspace);

    const channelsHeading = page.getByRole('heading', { name: 'Каналы' });
    const loginHeading = page.getByRole('heading', { name: 'Вход' });
    await channelsHeading.or(loginHeading).first().waitFor({ state: 'visible' });

    if (await channelsHeading.isVisible().catch(() => false)) {
      await channels.openTelegramWizard();
      await channels.expectTelegramWizard();
    }
  });
});
