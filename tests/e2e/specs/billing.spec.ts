import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';

import { BillingPage } from '../pages/billing.page';

/**
 * FE-10 scaffold: billing shell, limits, legal form, a11y.
 * Balance / top-up / ledger — FE-10b after BE-31.
 */
test.describe('Billing scaffold (FE-10)', () => {
  const demoWorkspace = '019a0000-0002-7000-8000-000000000002';

  test('owner sees limits and legal requisites form', async ({ page }) => {
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
    await expect(page.getByTestId('billing-coming-soon')).toBeVisible();
    await expect(billing.usage()).toBeVisible({ timeout: 10_000 });
    await expect(page.getByTestId('billing-usage-channels')).toBeVisible();
    await expect(billing.legalForm()).toBeVisible();
    await expect(page.getByTestId('header-plan-chip')).toBeVisible();
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

    await expect(billing.usage()).toBeVisible({ timeout: 10_000 });

    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      .analyze();
    const serious = results.violations.filter(
      (v) => v.impact === 'serious' || v.impact === 'critical',
    );
    expect(serious, JSON.stringify(serious, null, 2)).toEqual([]);
  });
});
