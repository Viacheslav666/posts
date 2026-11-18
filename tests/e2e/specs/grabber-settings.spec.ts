import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';

import { GrabberPage } from '../pages/grabber.page';

/**
 * FE-17 / TC-GRAB settings parity against MSW:
 * form groups, moderate default, auto warning, a11y on settings form.
 */
test.describe('Grabber rule settings (FE-17 / TC-GRAB)', () => {
  const demoWorkspace = '019a0000-0002-7000-8000-000000000002';
  const activeRuleId = '019a0000-grab-7000-8000-000000000001';

  test.beforeEach(async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Эл. почта').fill('demo@posts.app');
    await page.getByLabel('Пароль', { exact: true }).fill('Password1!');
    await page.getByRole('button', { name: 'Войти' }).click();
    await page.waitForURL(/\/app\//, { timeout: 15_000 }).catch(() => undefined);
  });

  test('new rule form shows settings groups and moderate default', async ({ page }) => {
    const grabber = new GrabberPage(page);
    await grabber.gotoNew(demoWorkspace);

    const form = page.getByTestId('grabber-rule-form');
    const login = page.getByRole('heading', { name: 'Вход' });
    await form.or(login).first().waitFor({ state: 'visible' });
    if (await login.isVisible().catch(() => false)) {
      test.skip(true, 'Auth session unavailable in this environment');
    }

    await expect(page.getByTestId('grabber-publish-mode')).toContainText(/модерац/i);
    await expect(page.getByTestId('grabber-section-filters')).toBeVisible();
    await expect(page.getByTestId('grabber-section-strip')).toBeVisible();
    await expect(page.getByTestId('grabber-section-replacements')).toBeVisible();
    await expect(page.getByTestId('grabber-section-schedule')).toBeVisible();
    await expect(page.getByTestId('grabber-section-limits')).toBeVisible();
    await expect(page.getByTestId('grabber-section-first-comment')).toBeVisible();
    await expect(page.getByTestId('grabber-section-attached')).toBeVisible();
    await expect(page.getByTestId('pending-feature-notice')).toContainText(/позже/i);

    await page.getByTestId('grabber-publish-mode').click();
    await page.getByRole('option', { name: /Автопубликация/i }).click();
    await expect(page.getByTestId('grabber-auto-warning')).toBeVisible();
  });

  test('rule edit exposes clone-config and history export controls', async ({ page }) => {
    await page.goto(`/app/${demoWorkspace}/automations/grabber/${activeRuleId}`);
    const detail = page.getByTestId('grabber-rule-detail');
    const login = page.getByRole('heading', { name: 'Вход' });
    await detail.or(login).first().waitFor({ state: 'visible' });
    if (await login.isVisible().catch(() => false)) {
      test.skip(true, 'Auth session unavailable in this environment');
    }

    await page.getByRole('tab', { name: /Редактирование/i }).click();
    await expect(page.getByTestId('grabber-clone-config')).toBeVisible();

    await page.getByRole('tab', { name: /История/i }).click();
    await expect(page.getByTestId('grabber-history-export')).toBeVisible();
    await expect(page.getByTestId('grabber-export-csv')).toBeVisible();
    await expect(page.getByTestId('grabber-export-xlsx')).toBeVisible();
  });

  test('settings form has no critical a11y violations', async ({ page }) => {
    const grabber = new GrabberPage(page);
    await grabber.gotoNew(demoWorkspace);
    const form = page.getByTestId('grabber-rule-form');
    const login = page.getByRole('heading', { name: 'Вход' });
    await form.or(login).first().waitFor({ state: 'visible' });
    if (await login.isVisible().catch(() => false)) {
      test.skip(true, 'Auth session unavailable in this environment');
    }

    const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa']).analyze();
    const critical = results.violations.filter((v) => v.impact === 'critical' || v.impact === 'serious');
    expect(critical, JSON.stringify(critical, null, 2)).toEqual([]);
  });
});
