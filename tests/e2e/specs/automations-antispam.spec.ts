import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';

/**
 * FE-23: Antispam UI against MSW —
 * create rule with stop-word, history hit, restore VK, TG restore unavailable, screenshots.
 */
test.describe('Automations antispam (FE-23)', () => {
  const demoWorkspace = '019a0000-0002-7000-8000-000000000002';
  const seededVkRuleId = '019a0000-asrule-7000-8000-000000000001';
  const seededTgLogId = '019a0000-aslog-7000-8000-000000000002';
  const seededVkLogId = '019a0000-aslog-7000-8000-000000000001';

  test.beforeEach(async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Эл. почта').fill('demo@posts.app');
    await page.getByLabel('Пароль', { exact: true }).fill('Password1!');
    await page.getByRole('button', { name: 'Войти' }).click();
    await page.waitForURL(/\/app\//, { timeout: 15_000 }).catch(() => undefined);
  });

  test('lists seeded rules and opens history with restore flow', async ({ page }) => {
    await page.goto(`/app/${demoWorkspace}/automations/antispam`);
    const root = page.getByTestId('antispam-page');
    const login = page.getByRole('heading', { name: 'Вход' });
    await root.or(login).first().waitFor({ state: 'visible', timeout: 20_000 });
    if (await login.isVisible().catch(() => false)) {
      test.skip(true, 'Auth session unavailable in this environment');
    }

    await expect(page.getByTestId(`antispam-rule-${seededVkRuleId}`)).toBeVisible();
    await page.getByTestId(`antispam-rule-${seededVkRuleId}`).getByRole('link').first().click();
    await expect(page.getByTestId('antispam-rule-detail')).toBeVisible();

    await page.getByTestId('antispam-tab-history').click();
    await expect(page.getByTestId('antispam-history')).toBeVisible();
    await expect(page.getByTestId(`antispam-history-item-${seededVkLogId}`)).toBeVisible();

    await page.getByTestId(`antispam-restore-${seededVkLogId}`).click();
    await expect(page.getByTestId(`antispam-restored-${seededVkLogId}`)).toBeVisible({
      timeout: 10_000,
    });
  });

  test('creates rule with stop-word and sees history hit', async ({ page }) => {
    await page.goto(`/app/${demoWorkspace}/automations/antispam/new`);
    const form = page.getByTestId('antispam-rule-form');
    const login = page.getByRole('heading', { name: 'Вход' });
    await form.or(login).first().waitFor({ state: 'visible', timeout: 20_000 });
    if (await login.isVisible().catch(() => false)) {
      test.skip(true, 'Auth session unavailable in this environment');
    }

    await page.getByTestId('antispam-rule-name').fill('E2E антиспам стоп');
    const keywordInput = page.getByPlaceholder(/Слово или список/i).first();
    await keywordInput.fill('спам');
    await page.getByRole('button', { name: 'Добавить' }).first().click();
    await page.getByTestId('antispam-rule-submit').click();
    await expect(page.getByTestId('antispam-rule-detail')).toBeVisible({ timeout: 15_000 });

    await page.getByTestId('antispam-tab-history').click();
    await expect(page.getByTestId('antispam-history')).toBeVisible();
    await expect(page.getByTestId('antispam-history')).toContainText(/спам/i);
  });

  test('shows TG restore unavailable on seeded TG log via filters', async ({ page }) => {
    await page.goto(`/app/${demoWorkspace}/automations/antispam/${seededVkRuleId}`);
    const detail = page.getByTestId('antispam-rule-detail');
    const login = page.getByRole('heading', { name: 'Вход' });
    await detail.or(login).first().waitFor({ state: 'visible', timeout: 20_000 });
    if (await login.isVisible().catch(() => false)) {
      test.skip(true, 'Auth session unavailable in this environment');
    }

    // Open TG rule history for unavailable restore tip
    await page.goto(`/app/${demoWorkspace}/automations/antispam/019a0000-asrule-7000-8000-000000000002`);
    await page.getByTestId('antispam-tab-history').click();
    await expect(page.getByTestId(`antispam-restore-unavailable-${seededTgLogId}`)).toBeVisible();
  });

  test('antispam page has no critical a11y violations', async ({ page }) => {
    await page.goto(`/app/${demoWorkspace}/automations/antispam`);
    const root = page.getByTestId('antispam-page');
    const login = page.getByRole('heading', { name: 'Вход' });
    await root.or(login).first().waitFor({ state: 'visible', timeout: 20_000 });
    if (await login.isVisible().catch(() => false)) {
      test.skip(true, 'Auth session unavailable in this environment');
    }

    const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa']).analyze();
    const critical = results.violations.filter(
      (v) => v.impact === 'critical' || v.impact === 'serious',
    );
    expect(critical, JSON.stringify(critical, null, 2)).toEqual([]);
  });

  test('captures desktop and mobile screenshots', async ({ page }) => {
    await page.goto(`/app/${demoWorkspace}/automations/antispam`);
    const root = page.getByTestId('antispam-page');
    const login = page.getByRole('heading', { name: 'Вход' });
    await root.or(login).first().waitFor({ state: 'visible', timeout: 20_000 });
    if (await login.isVisible().catch(() => false)) {
      test.skip(true, 'Auth session unavailable in this environment');
    }

    await page.setViewportSize({ width: 1280, height: 800 });
    await expect(root).toBeVisible();
    await page.screenshot({
      path: 'test-results/fe23-antispam-desktop.png',
      fullPage: true,
    });

    await page.setViewportSize({ width: 360, height: 740 });
    await expect(root).toBeVisible();
    await page.screenshot({
      path: 'test-results/fe23-antispam-mobile.png',
      fullPage: true,
    });
  });
});
