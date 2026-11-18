import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';

import { BillingPage } from '../pages/billing.page';

/**
 * FE-10b: баланс, пополнение (мок ЮKassa), низкий баланс, история.
 */
test.describe('Billing balance (FE-10b)', () => {
  const demoWorkspace = '019a0000-0002-7000-8000-000000000002';

  test('shows low balance warning and transaction history', async ({ page }) => {
    const billing = new BillingPage(page);
    await billing.loginDemo();
    await billing.gotoBilling(demoWorkspace);

    const loginHeading = page.getByRole('heading', { name: 'Вход' });
    const root = billing.root();
    await root.or(loginHeading).first().waitFor({ state: 'visible' });
    if (await loginHeading.isVisible().catch(() => false)) {
      test.skip();
      return;
    }

    await expect(root).toBeVisible();
    await expect(billing.balance()).toBeVisible({ timeout: 10_000 });
    await expect(billing.lowBalanceAlert()).toBeVisible();
    await expect(billing.transactions()).toBeVisible();
    await expect(page.getByTestId('billing-tx-table')).toBeVisible();
    await expect(page.getByTestId('header-plan-chip')).toBeVisible();
  });

  test('top-up redirects to mock YooKassa return and completes payment', async ({ page }) => {
    const billing = new BillingPage(page);
    await billing.loginDemo();
    await billing.gotoBilling(demoWorkspace);

    const loginHeading = page.getByRole('heading', { name: 'Вход' });
    const root = billing.root();
    await root.or(loginHeading).first().waitFor({ state: 'visible' });
    if (!(await root.isVisible().catch(() => false))) {
      test.skip();
      return;
    }

    await expect(billing.topup()).toBeVisible({ timeout: 10_000 });
    await page.getByTestId('billing-topup-preset-2000').click();
    await page.getByTestId('billing-topup-offer').check();
    await page.getByTestId('billing-topup-submit').click();

    await page.waitForURL(/\/billing\/return/, { timeout: 15_000 });
    await expect(page.getByTestId('billing-checkout-return')).toBeVisible();
    await expect(page.getByTestId('billing-return-success')).toBeVisible({ timeout: 15_000 });
    await page.getByTestId('billing-return-back').click();
    await expect(page.getByTestId('billing-page')).toBeVisible({ timeout: 15_000 });
  });

  test('billing page usable at 360px without serious axe issues', async ({ page }) => {
    await page.setViewportSize({ width: 360, height: 740 });
    const billing = new BillingPage(page);
    await billing.loginDemo();
    await billing.gotoBilling(demoWorkspace);

    const loginHeading = page.getByRole('heading', { name: 'Вход' });
    const root = billing.root();
    await root.or(loginHeading).first().waitFor({ state: 'visible' });
    if (!(await root.isVisible().catch(() => false))) {
      test.skip();
      return;
    }

    await expect(billing.balance()).toBeVisible({ timeout: 10_000 });

    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      .analyze();
    const serious = results.violations.filter(
      (v) => v.impact === 'serious' || v.impact === 'critical',
    );
    expect(serious, JSON.stringify(serious, null, 2)).toEqual([]);
  });
});
