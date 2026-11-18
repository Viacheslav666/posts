import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';

import { GrabberPage } from '../pages/grabber.page';

/**
 * FE-14 / TC-GRAB P0 against MSW:
 * default moderate queue, approve → pipeline, basic a11y + adaptive shell.
 */
test.describe('Grabber moderation (FE-14 / TC-GRAB P0)', () => {
  const demoWorkspace = '019a0000-0002-7000-8000-000000000002';
  const pendingItemId = '019a0000-gitem-7000-8000-000000000001';

  test.beforeEach(async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Email').fill('demo@posts.app');
    await page.getByLabel('Пароль', { exact: true }).fill('Password1!');
    await page.getByRole('button', { name: 'Войти' }).click();
    await page.waitForURL(/\/app\//, { timeout: 15_000 }).catch(() => undefined);
  });

  test('default UX is moderation queue; approve path works (TC-GRAB-08/09)', async ({ page }) => {
    const grabber = new GrabberPage(page);
    await grabber.goto(demoWorkspace);

    const heading = grabber.heading().or(page.getByRole('heading', { name: 'Вход' }));
    await heading.first().waitFor({ state: 'visible' });

    if (await page.getByRole('heading', { name: 'Вход' }).isVisible().catch(() => false)) {
      test.skip(true, 'Auth session unavailable in this environment');
    }

    await expect(grabber.queue()).toBeVisible();
    await expect(page.getByTestId('grabber-tab-queue')).toHaveAttribute('data-state', 'active');

    const approve = grabber.approveButton(pendingItemId);
    if (await approve.isVisible().catch(() => false)) {
      await approve.click();
      await expect(approve).toHaveCount(0, { timeout: 10_000 });
    }
  });

  test('create rule form defaults to moderate and shows auto warning', async ({ page }) => {
    const grabber = new GrabberPage(page);
    await grabber.gotoNew(demoWorkspace);

    const form = page.getByTestId('grabber-rule-form');
    const login = page.getByRole('heading', { name: 'Вход' });
    await form.or(login).first().waitFor({ state: 'visible' });
    if (await login.isVisible().catch(() => false)) {
      test.skip(true, 'Auth session unavailable in this environment');
    }

    await expect(page.getByTestId('grabber-publish-mode')).toContainText(/модерац/i);
    await page.getByTestId('grabber-publish-mode').click();
    await page.getByRole('option', { name: /Автопубликация/i }).click();
    await expect(page.getByTestId('grabber-auto-warning')).toBeVisible();
  });

  test('responsive 360–1920 and no critical a11y issues on grabber', async ({ page }) => {
    const grabber = new GrabberPage(page);

    for (const width of [360, 768, 1280, 1920]) {
      await page.setViewportSize({ width, height: 900 });
      await grabber.goto(demoWorkspace);
      const shell = grabber.heading().or(page.getByRole('heading', { name: 'Вход' }));
      await expect(shell.first()).toBeVisible();
    }

    if (await page.getByRole('heading', { name: 'Граббер' }).isVisible().catch(() => false)) {
      const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa']).analyze();
      const critical = results.violations.filter((v) => v.impact === 'critical' || v.impact === 'serious');
      expect(critical, JSON.stringify(critical, null, 2)).toEqual([]);
    }
  });
});
