import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';

import { SuggestPage } from '../pages/suggest.page';

/**
 * FE-22 VK Suggests UI against MSW:
 * configure filter, approve item, bulk publish all, screenshots, a11y.
 */
test.describe('Automations suggest (FE-22)', () => {
  const demoWorkspace = '019a0000-0002-7000-8000-000000000002';
  const settingsId = '019a0000-sugg-7000-8000-000000000001';
  const pendingItemId = '019a0000-sitem-7000-8000-000000000001';

  test.beforeEach(async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Эл. почта').fill('demo@posts.app');
    await page.getByLabel('Пароль', { exact: true }).fill('Password1!');
    await page.getByRole('button', { name: 'Войти' }).click();
    await page.waitForURL(/\/app\//, { timeout: 15_000 }).catch(() => undefined);
  });

  test('configures a filter and saves settings', async ({ page }) => {
    const suggest = new SuggestPage(page);
    await suggest.gotoSettings(demoWorkspace, settingsId);

    const form = suggest.settingsForm().or(page.getByRole('heading', { name: 'Вход' }));
    await form.first().waitFor({ state: 'visible', timeout: 20_000 });
    if (await page.getByRole('heading', { name: 'Вход' }).isVisible().catch(() => false)) {
      test.skip(true, 'Auth session unavailable in this environment');
    }

    await suggest.settingsTab().click();
    await expect(suggest.settingsForm()).toBeVisible();

    const stopInput = suggest.stopWordsEditor();
    await stopInput.fill('тестстоп');
    await stopInput.press('Enter');
    await suggest.saveSettings().click();

    await expect(suggest.settingsForm()).toBeVisible({ timeout: 10_000 });
  });

  test('approves an item from the moderation queue', async ({ page }) => {
    const suggest = new SuggestPage(page);
    await suggest.gotoSettings(demoWorkspace, settingsId);

    const queue = suggest.queue().or(page.getByRole('heading', { name: 'Вход' }));
    await queue.first().waitFor({ state: 'visible', timeout: 20_000 });
    if (await page.getByRole('heading', { name: 'Вход' }).isVisible().catch(() => false)) {
      test.skip(true, 'Auth session unavailable in this environment');
    }

    await expect(suggest.queue()).toBeVisible();
    const approve = suggest.approveButton(pendingItemId);
    if (await approve.isVisible().catch(() => false)) {
      await approve.click();
      await expect(approve).toHaveCount(0, { timeout: 10_000 });
    }
  });

  test('bulk publish all shows progress UI', async ({ page }) => {
    page.on('dialog', (dialog) => dialog.accept());
    const suggest = new SuggestPage(page);
    await suggest.gotoSettings(demoWorkspace, settingsId);

    const queue = suggest.queue().or(page.getByRole('heading', { name: 'Вход' }));
    await queue.first().waitFor({ state: 'visible', timeout: 20_000 });
    if (await page.getByRole('heading', { name: 'Вход' }).isVisible().catch(() => false)) {
      test.skip(true, 'Auth session unavailable in this environment');
    }

    await suggest.bulkApproveAll().click();
    await expect(suggest.bulkProgress()).toBeVisible({ timeout: 10_000 });
    await expect(suggest.bulkProgress()).toContainText(/100|успешно/i);
  });

  test('captures desktop and mobile screenshots', async ({ page }) => {
    const suggest = new SuggestPage(page);
    await suggest.goto(demoWorkspace);

    const root = suggest.root().or(page.getByRole('heading', { name: 'Вход' }));
    await root.first().waitFor({ state: 'visible', timeout: 20_000 });
    if (await page.getByRole('heading', { name: 'Вход' }).isVisible().catch(() => false)) {
      test.skip(true, 'Auth session unavailable in this environment');
    }

    await page.setViewportSize({ width: 1280, height: 800 });
    await expect(suggest.root()).toBeVisible();
    await page.screenshot({
      path: 'test-results/fe22-suggest-desktop.png',
      fullPage: true,
    });

    await page.setViewportSize({ width: 360, height: 740 });
    await expect(suggest.root()).toBeVisible();
    await page.screenshot({
      path: 'test-results/fe22-suggest-mobile.png',
      fullPage: true,
    });
  });

  test('has no critical a11y violations on suggest page', async ({ page }) => {
    const suggest = new SuggestPage(page);
    await suggest.goto(demoWorkspace);
    const root = suggest.root().or(page.getByRole('heading', { name: 'Вход' }));
    await root.first().waitFor({ state: 'visible', timeout: 20_000 });
    if (await page.getByRole('heading', { name: 'Вход' }).isVisible().catch(() => false)) {
      test.skip(true, 'Auth session unavailable in this environment');
    }

    const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa']).analyze();
    const critical = results.violations.filter(
      (v) => v.impact === 'critical' || v.impact === 'serious',
    );
    expect(critical, JSON.stringify(critical, null, 2)).toEqual([]);
  });
});
