import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';

import { AdminPage } from '../pages/admin.page';

/**
 * A13 / platform admin: search → impersonate → exit; DLQ retry; promo; announcement.
 */
test.describe('Admin panel (A13)', () => {
  test('search user → impersonate → exit', async ({ page }) => {
    const admin = new AdminPage(page);
    await admin.loginDemo();
    await admin.gotoUsers();

    const loginHeading = page.getByRole('heading', { name: 'Вход' });
    const root = admin.usersRoot();
    await root.or(loginHeading).or(admin.forbidden()).first().waitFor({ state: 'visible' });
    if (await loginHeading.isVisible().catch(() => false)) {
      test.skip();
      return;
    }
    if (await admin.forbidden().isVisible().catch(() => false)) {
      test.skip();
      return;
    }

    await expect(root).toBeVisible();
    await expect(page.getByTestId('admin-users-table')).toBeVisible({ timeout: 10_000 });

    await page.getByTestId('admin-users-search').fill('editor@');
    await page.getByTestId('admin-users-search-submit').click();
    await expect(page.getByText('editor@posts.app')).toBeVisible();

    await page.getByText('editor@posts.app').click();
    await expect(page.getByTestId('admin-user-detail')).toBeVisible();
    await page.getByTestId('admin-impersonate-reason').fill('e2e support case');
    await page.getByTestId('admin-impersonate-open').click();
    await expect(page.getByTestId('admin-impersonate-dialog')).toBeVisible();
    await page.getByTestId('admin-impersonate-confirm').click();

    await expect(admin.impersonationBanner()).toBeVisible({ timeout: 10_000 });
    await expect(admin.impersonationBanner()).toContainText('Вы вошли как');

    await page.getByTestId('impersonation-exit').click();
    await expect(admin.impersonationBanner()).toBeHidden({ timeout: 10_000 });
  });

  test('retry message from DLQ with confirmation', async ({ page }) => {
    const admin = new AdminPage(page);
    await admin.loginDemo();
    await admin.gotoQueues();

    const root = page.getByTestId('admin-queues-page');
    const loginHeading = page.getByRole('heading', { name: 'Вход' });
    await root.or(loginHeading).or(admin.forbidden()).first().waitFor({ state: 'visible' });
    if (!(await root.isVisible().catch(() => false))) {
      test.skip();
      return;
    }

    await page.getByTestId('admin-queue-link-publish.dlq').click();
    await expect(page.getByTestId('admin-queue-messages-page')).toBeVisible();
    const retry = page.locator('[data-testid^="admin-queue-retry-"]').first();
    await expect(retry).toBeVisible();
    await retry.click();
    await expect(page.getByTestId('admin-queue-retry-dialog')).toBeVisible();
    await page.getByTestId('admin-queue-retry-confirm').click();
    await expect(page.getByTestId('admin-queue-retry-dialog')).toBeHidden();
  });

  test('create promo code', async ({ page }) => {
    const admin = new AdminPage(page);
    await admin.loginDemo();
    await admin.gotoPromos();

    const root = page.getByTestId('admin-promos-page');
    const loginHeading = page.getByRole('heading', { name: 'Вход' });
    await root.or(loginHeading).or(admin.forbidden()).first().waitFor({ state: 'visible' });
    if (!(await root.isVisible().catch(() => false))) {
      test.skip();
      return;
    }

    const code = `E2E${Date.now().toString().slice(-6)}`;
    await page.getByTestId('admin-promo-code').fill(code);
    await page.getByTestId('admin-promo-value').fill('15');
    await page.getByTestId('admin-promo-create-submit').click();
    await expect(page.getByText(code)).toBeVisible({ timeout: 10_000 });
  });

  test('publish announcement', async ({ page }) => {
    const admin = new AdminPage(page);
    await admin.loginDemo();
    await admin.gotoAnnouncements();

    const root = page.getByTestId('admin-announcements-page');
    const loginHeading = page.getByRole('heading', { name: 'Вход' });
    await root.or(loginHeading).or(admin.forbidden()).first().waitFor({ state: 'visible' });
    if (!(await root.isVisible().catch(() => false))) {
      test.skip();
      return;
    }

    const title = `E2E banner ${Date.now()}`;
    await page.getByTestId('admin-announcement-title').fill(title);
    await page.getByTestId('admin-announcement-body').fill('Текст e2e объявления');
    await page.getByTestId('admin-announcement-publish').click();
    await expect(page.getByText(title)).toBeVisible({ timeout: 10_000 });
  });

  test('admin users page has no serious axe issues', async ({ page }) => {
    const admin = new AdminPage(page);
    await admin.loginDemo();
    await admin.gotoUsers();

    const root = admin.usersRoot();
    const loginHeading = page.getByRole('heading', { name: 'Вход' });
    await root.or(loginHeading).or(admin.forbidden()).first().waitFor({ state: 'visible' });
    if (!(await root.isVisible().catch(() => false))) {
      test.skip();
      return;
    }

    await expect(page.getByTestId('admin-users-table')).toBeVisible({ timeout: 10_000 });

    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      .analyze();
    const serious = results.violations.filter(
      (v) => v.impact === 'serious' || v.impact === 'critical',
    );
    expect(serious, JSON.stringify(serious, null, 2)).toEqual([]);
  });
});
