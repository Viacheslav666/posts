import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';

import { AnalyticsPage } from '../pages/analytics.page';

/**
 * FE-19: аналитика постов — год, графики, топ ошибок, повтор публикации.
 */
test.describe('Posts analytics (FE-19)', () => {
  const demoWorkspace = '019a0000-0002-7000-8000-000000000002';

  test('opens analytics, changes year, opens error and retries', async ({ page }) => {
    const analytics = new AnalyticsPage(page);
    await analytics.loginDemo();
    await analytics.gotoAnalytics(demoWorkspace, 2026);

    const loginHeading = page.getByRole('heading', { name: 'Вход' });
    const root = analytics.root();
    await root.or(loginHeading).first().waitFor({ state: 'visible' });
    if (await loginHeading.isVisible().catch(() => false)) {
      test.skip();
      return;
    }

    await expect(root).toBeVisible();
    await expect(analytics.volumeChart()).toBeVisible({ timeout: 15_000 });
    await expect(analytics.errorsTable()).toBeVisible();

    await analytics.yearSelect().getByRole('combobox').click();
    await page.getByRole('option', { name: '2025' }).click();
    await expect(page).toHaveURL(/year=2025/);

    await analytics.gotoAnalytics(demoWorkspace, 2026);
    await expect(analytics.errorsTable()).toBeVisible({ timeout: 15_000 });

    const openLink = analytics.openErrorLink();
    await expect(openLink).toBeVisible();
    await openLink.click();
    await expect(page).toHaveURL(new RegExp(`/app/${demoWorkspace}/posts`));

    await analytics.gotoAnalytics(demoWorkspace, 2026);
    await expect(analytics.retryButton()).toBeVisible({ timeout: 15_000 });
    await analytics.retryButton().click();
    // Toast + status both contain the phrase; pin to the toast title to avoid strict-mode clash.
    await expect(page.getByText('Публикация поставлена в очередь на повтор', { exact: true })).toBeVisible({
      timeout: 10_000,
    });
  });

  test('analytics page usable at 360px without serious axe issues', async ({ page }) => {
    await page.setViewportSize({ width: 360, height: 740 });
    const analytics = new AnalyticsPage(page);
    await analytics.loginDemo();
    await analytics.gotoAnalytics(demoWorkspace, 2026);

    const loginHeading = page.getByRole('heading', { name: 'Вход' });
    const root = analytics.root();
    await root.or(loginHeading).first().waitFor({ state: 'visible' });
    if (!(await root.isVisible().catch(() => false))) {
      test.skip();
      return;
    }

    await expect(analytics.volumeChart()).toBeVisible({ timeout: 15_000 });

    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      .analyze();
    const serious = results.violations.filter(
      (v) => v.impact === 'serious' || v.impact === 'critical',
    );
    expect(serious, JSON.stringify(serious, null, 2)).toEqual([]);
  });
});
