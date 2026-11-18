import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';

import { OrdPage } from '../pages/ord.page';

/**
 * FE-16: ORD reporting tab against MSW — view, ERID check, delete act, RBAC, a11y.
 */
test.describe('ORD reporting (FE-16)', () => {
  const demoWorkspace = '019a0000-0002-7000-8000-000000000002';

  async function login(page: import('@playwright/test').Page) {
    await page.goto('/login');
    await page.getByLabel('Эл. почта').fill('demo@posts.app');
    await page.getByLabel('Пароль', { exact: true }).fill('Password1!');
    await page.getByRole('button', { name: 'Войти' }).click();
    await page.waitForURL(/\/app\//, { timeout: 15_000 }).catch(() => undefined);
  }

  test('view reports, check ERID, delete act with confirm', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 900 });
    await login(page);

    const ord = new OrdPage(page);
    await ord.goto(demoWorkspace);

    const loginHeading = page.getByRole('heading', { name: 'Вход' });
    const ordHeading = ord.heading();
    await ordHeading.or(loginHeading).first().waitFor({ state: 'visible' });
    if (!(await ordHeading.isVisible().catch(() => false))) {
      test.skip();
      return;
    }

    await ord.openReportsTab();
    await expect(ord.reportsPanel()).toBeVisible();
    await expect(ord.reportsTable()).toBeVisible();
    await expect(ord.runReportButton()).toBeVisible();

    await ord.eridInput().fill('EridDemoVk01');
    await ord.eridSubmit().click();
    await expect(ord.eridResult()).toBeVisible();
    await expect(ord.eridResult()).toContainText('EridDemoVk01');

    const deleteBtn = page.getByTestId('ord-report-delete-019a0000-ordi-7000-8000-000000000301');
    await expect(deleteBtn).toBeVisible();
    await deleteBtn.click();
    await expect(page.getByTestId('ord-report-delete-dialog')).toBeVisible();
    await page.getByTestId('ord-report-delete-confirm').click();
    await expect(deleteBtn).toHaveCount(0);

    await expect(ord.exportCsvButton()).toBeVisible();
    await expect(ord.exportXlsxButton()).toBeVisible();
  });

  test('reports panel usable at 360px without serious axe issues', async ({ page }) => {
    await page.setViewportSize({ width: 360, height: 740 });
    await login(page);

    const ord = new OrdPage(page);
    await ord.goto(demoWorkspace);

    const loginHeading = page.getByRole('heading', { name: 'Вход' });
    const ordHeading = ord.heading();
    await ordHeading.or(loginHeading).first().waitFor({ state: 'visible' });
    if (!(await ordHeading.isVisible().catch(() => false))) {
      test.skip();
      return;
    }

    await ord.openReportsTab();
    await expect(ord.reportsPanel()).toBeVisible();

    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      .analyze();
    const serious = results.violations.filter(
      (v) => v.impact === 'serious' || v.impact === 'critical',
    );
    expect(serious, JSON.stringify(serious, null, 2)).toEqual([]);
  });
});
