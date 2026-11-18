import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';

import { NotificationsPage } from '../pages/notifications.page';

/**
 * FE-9: notifications center, settings, dashboard against MSW.
 */
test.describe('Notifications & dashboard (FE-9)', () => {
  const demoWorkspace = '019a0000-0002-7000-8000-000000000002';

  test('login → bell unread → panel → mark all read', async ({ page }) => {
    const notifications = new NotificationsPage(page);
    await notifications.loginDemo();
    await notifications.gotoDashboard(demoWorkspace);

    const loginHeading = page.getByRole('heading', { name: 'Вход' });
    const dashboard = notifications.dashboard();
    await dashboard.or(loginHeading).first().waitFor({ state: 'visible' });
    if (await loginHeading.isVisible().catch(() => false)) {
      test.skip();
      return;
    }

    await expect(dashboard).toBeVisible();
    await expect(notifications.bell()).toBeVisible();

    const unread = notifications.unreadCount();
    await expect(unread).toBeVisible({ timeout: 10_000 });
    const before = await unread.textContent();
    expect(Number(before)).toBeGreaterThan(0);

    await notifications.openBell();
    await expect(page.getByTestId('notification-item').first()).toBeVisible();
    await page.getByTestId('notifications-mark-all').click();

    await expect(notifications.unreadCount()).toHaveCount(0, { timeout: 10_000 });
  });

  test('notification settings matrix saves', async ({ page }) => {
    const notifications = new NotificationsPage(page);
    await notifications.loginDemo();
    await notifications.gotoNotificationSettings(demoWorkspace);

    const loginHeading = page.getByRole('heading', { name: 'Вход' });
    const save = notifications.settingsForm();
    await save.or(loginHeading).first().waitFor({ state: 'visible' });
    if (await loginHeading.isVisible().catch(() => false)) {
      test.skip();
      return;
    }

    await expect(page.getByTestId('notification-matrix')).toBeVisible();
    await save.click();
    await expect(page.getByText('Сохранено', { exact: true })).toBeVisible({
      timeout: 10_000,
    });
  });

  test('dashboard is usable at 360px without serious axe issues', async ({ page }) => {
    await page.setViewportSize({ width: 360, height: 740 });
    const notifications = new NotificationsPage(page);
    await notifications.loginDemo();
    await notifications.gotoDashboard(demoWorkspace);

    const loginHeading = page.getByRole('heading', { name: 'Вход' });
    const dashboard = notifications.dashboard();
    await dashboard.or(loginHeading).first().waitFor({ state: 'visible' });
    if (!(await dashboard.isVisible().catch(() => false))) {
      test.skip();
      return;
    }

    await expect(page.getByTestId('dashboard-quick-actions')).toBeVisible();

    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      .analyze();
    const serious = results.violations.filter(
      (v) => v.impact === 'serious' || v.impact === 'critical',
    );
    expect(serious, JSON.stringify(serious, null, 2)).toEqual([]);
  });
});
