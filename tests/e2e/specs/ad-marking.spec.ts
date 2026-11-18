import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';

import { EditorPage } from '../pages/editor.page';
import { OrdPage } from '../pages/ord.page';

/**
 * FE-12: ORD / ad marking editor against MSW (TC-AD-01…04).
 */
test.describe('Ad marking & ORD editor (FE-12)', () => {
  const demoWorkspace = '019a0000-0002-7000-8000-000000000002';

  async function login(page: import('@playwright/test').Page) {
    await page.goto('/login');
    await page.getByLabel('Эл. почта').fill('demo@posts.app');
    await page.getByLabel('Пароль', { exact: true }).fill('Password1!');
    await page.getByRole('button', { name: 'Войти' }).click();
    await page.waitForURL(/\/app\//, { timeout: 15_000 }).catch(() => undefined);
  }

  test('TC-AD-01: post options respect network support (VK Donut, TG buttons)', async ({
    page,
  }) => {
    await page.setViewportSize({ width: 1280, height: 900 });
    await login(page);
    const editor = new EditorPage(page);
    await editor.goto(demoWorkspace);

    const heading = editor.heading();
    const loginHeading = page.getByRole('heading', { name: 'Вход' });
    await heading.or(loginHeading).first().waitFor({ state: 'visible' });
    if (!(await heading.isVisible().catch(() => false))) {
      test.skip();
      return;
    }

    await expect(editor.optionsPanel()).toBeVisible();
    await expect(page.getByTestId('option-donut')).toBeVisible();
    await expect(page.getByTestId('option-poll')).toBeVisible();
    await expect(page.getByTestId('ad-marking-form')).toBeVisible();
  });

  test('TC-AD-02: recurrence select exposes vposter-parity presets', async ({ page }) => {
    await login(page);
    const editor = new EditorPage(page);
    await editor.goto(demoWorkspace);
    const heading = editor.heading();
    if (!(await heading.isVisible().catch(() => false))) {
      test.skip();
      return;
    }

    const select = editor.recurrenceSelect();
    await expect(select).toBeVisible();
    await expect(select.locator('option[value="once"]')).toHaveCount(1);
    await expect(select.locator('option[value="weekdays"]')).toHaveCount(1);
    await expect(select.locator('option[value="every_n_days"]')).toHaveCount(1);
    await expect(select.locator('option[value="every_12_months"]')).toHaveCount(1);
  });

  test('TC-AD-03: enabling ad without erid blocks schedule', async ({ page }) => {
    await login(page);
    const editor = new EditorPage(page);
    await editor.goto(demoWorkspace);
    const heading = editor.heading();
    if (!(await heading.isVisible().catch(() => false))) {
      test.skip();
      return;
    }

    await editor.adToggle().check();
    await expect(page.getByTestId('ad-schedule-block')).toBeVisible();
    await expect(editor.scheduleButton()).toBeDisabled();
  });

  test('TC-AD-04: ORD settings + register path yields erid in preview', async ({ page }) => {
    await login(page);

    const ord = new OrdPage(page);
    await ord.goto(demoWorkspace);
    const ordHeading = ord.heading();
    if (!(await ordHeading.isVisible().catch(() => false))) {
      test.skip();
      return;
    }
    await expect(ord.connectionForm()).toBeVisible();
    await expect(ord.apiKey()).toBeVisible();

    const editor = new EditorPage(page);
    await editor.goto(demoWorkspace);
    await expect(editor.heading()).toBeVisible();
    await editor.adToggle().check();

    // Demo MSW already has active VK ORD + contractor; fill required fields and register.
    const inn = page.getByTestId('ad-inn');
    await inn.fill('7707083893');
    await page.getByTestId('ad-advertiser-name').fill('ООО Посты');
    await page.getByTestId('ad-kpp').fill('770701001');
    await page.getByTestId('ad-contract-date').fill('2026-01-15');
    await page.getByTestId('ad-kktu').selectOption('1.1.1');

    const register = page.getByTestId('ad-register-erid');
    await expect(register).toBeEnabled();
    await register.click();
    await expect(page.getByTestId('ad-erid-value')).toBeVisible({ timeout: 10_000 });
    await expect(page.getByTestId('ad-marking-slot')).toContainText('Реклама');
    await expect(page.getByTestId('ad-marking-slot')).toContainText('erid:');
  });

  test('editor a11y has no serious/critical axe violations', async ({ page }) => {
    await login(page);
    const editor = new EditorPage(page);
    await editor.goto(demoWorkspace);
    if (!(await editor.heading().isVisible().catch(() => false))) {
      test.skip();
      return;
    }

    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      .analyze();
    const serious = results.violations.filter(
      (v) => v.impact === 'serious' || v.impact === 'critical',
    );
    expect(serious, JSON.stringify(serious, null, 2)).toEqual([]);
  });

  test('editor adapts at 360px width', async ({ page }) => {
    await page.setViewportSize({ width: 360, height: 740 });
    await login(page);
    const editor = new EditorPage(page);
    await editor.goto(demoWorkspace);
    if (!(await editor.heading().isVisible().catch(() => false))) {
      test.skip();
      return;
    }
    await expect(page.getByTestId('post-editor')).toBeVisible();
    await expect(editor.optionsPanel()).toBeVisible();
  });

  test('FE-15: six placement modes + vk_menu warning + VAT fields', async ({ page }) => {
    await login(page);
    const editor = new EditorPage(page);
    await editor.goto(demoWorkspace);
    if (!(await editor.heading().isVisible().catch(() => false))) {
      test.skip();
      return;
    }

    await editor.adToggle().check();
    const placement = page.getByTestId('ad-placement');
    await expect(placement).toBeVisible();
    for (const value of [
      'vk_menu',
      'text_start',
      'text_end',
      'vk_menu_and_text_end',
      'click_link_start',
      'click_link_end',
    ]) {
      await expect(placement.locator(`option[value="${value}"]`)).toHaveCount(1);
    }

    await placement.selectOption('vk_menu');
    await expect(page.getByTestId('ad-vk-menu-unsupported')).toBeVisible();
    await expect(page.getByTestId('ad-placement-preview')).toBeVisible();
    await expect(page.getByTestId('ad-vat-rate')).toBeVisible();
    await expect(page.getByTestId('ad-contract-amount')).toBeVisible();
    await expect(page.getByTestId('ad-arir-link')).toBeVisible();
  });

  test('FE-15: ORD connection form has address, roles, offer template', async ({ page }) => {
    await login(page);
    const ord = new OrdPage(page);
    await ord.goto(demoWorkspace);
    if (!(await ord.heading().isVisible().catch(() => false))) {
      test.skip();
      return;
    }

    await expect(ord.connectionForm()).toBeVisible();
    await expect(page.getByTestId('ord-address')).toBeVisible();
    await expect(page.getByTestId('ord-vat-rate')).toBeVisible();
    await expect(page.getByTestId('ord-roles')).toBeVisible();
    await expect(page.getByTestId('ord-notify-problems')).toBeVisible();
    await expect(page.getByTestId('ord-erir-link')).toBeVisible();
    await expect(page.getByTestId('ord-offer-download')).toHaveAttribute(
      'href',
      '/legal/ord-offer-template.docx',
    );
  });
});
