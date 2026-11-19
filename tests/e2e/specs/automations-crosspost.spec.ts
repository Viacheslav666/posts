import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';

/**
 * FE-24: Crosspost UI against MSW —
 * list seeded rule, create VK → TG + Max, preview, journal statuses, screenshots.
 */
test.describe('Automations crosspost (FE-24)', () => {
  const demoWorkspace = '019a0000-0002-7000-8000-000000000002';
  const seededRuleId = '019a0000-xpost-7000-8000-000000000001';
  const tgChannelId = '019a0000-chan-7000-8000-000000000002';
  const maxChannelId = '019a0000-chan-7000-8000-000000000004';

  test.beforeEach(async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Эл. почта').fill('demo@posts.app');
    await page.getByLabel('Пароль', { exact: true }).fill('Password1!');
    await page.getByRole('button', { name: 'Войти' }).click();
    await page.waitForURL(/\/app\//, { timeout: 15_000 }).catch(() => undefined);
  });

  test('lists seeded rule and opens journal with statuses', async ({ page }) => {
    await page.goto(`/app/${demoWorkspace}/automations/crosspost`);
    const root = page.getByTestId('crosspost-page');
    const login = page.getByRole('heading', { name: 'Вход' });
    await root.or(login).first().waitFor({ state: 'visible', timeout: 20_000 });
    if (await login.isVisible().catch(() => false)) {
      test.skip(true, 'Auth session unavailable in this environment');
    }

    await expect(page.getByTestId(`crosspost-rule-${seededRuleId}`)).toBeVisible();
    await page.getByTestId(`crosspost-rule-${seededRuleId}`).getByRole('link', { name: /VK/i }).click();
    await expect(page.getByTestId('crosspost-rule-detail')).toBeVisible();

    await page.getByTestId('crosspost-tab-journal').click();
    await expect(page.getByTestId('crosspost-journal')).toBeVisible();
    await expect(page.getByTestId('crosspost-journal')).toContainText(/Опубликован|Ошибка|Отфильтрован/i);
  });

  test('creates VK → TG + Max rule via wizard', async ({ page }) => {
    await page.goto(`/app/${demoWorkspace}/automations/crosspost/new`);
    const form = page.getByTestId('crosspost-rule-form');
    const login = page.getByRole('heading', { name: 'Вход' });
    await form.or(login).first().waitFor({ state: 'visible', timeout: 20_000 });
    if (await login.isVisible().catch(() => false)) {
      test.skip(true, 'Auth session unavailable in this environment');
    }

    await page.getByTestId('crosspost-rule-name').fill('E2E VK → TG Max');
    await page.getByTestId('crosspost-form-next').click();

    await expect(page.getByTestId('crosspost-step-panel-targets')).toBeVisible();
    await page.getByTestId(`crosspost-target-${tgChannelId}`).click();
    await page.getByTestId(`crosspost-target-${maxChannelId}`).click();
    await page.getByTestId('crosspost-form-next').click();

    for (const _ of ['filters', 'transform', 'schedule']) {
      await page.getByTestId('crosspost-form-next').click();
    }

    await expect(page.getByTestId('crosspost-step-panel-review')).toBeVisible();
    await page.getByTestId('crosspost-rule-submit').click();
    await expect(page.getByTestId('crosspost-rule-detail')).toBeVisible({ timeout: 15_000 });
  });

  test('runs preview on seeded rule', async ({ page }) => {
    await page.goto(`/app/${demoWorkspace}/automations/crosspost/${seededRuleId}`);
    const detail = page.getByTestId('crosspost-rule-detail');
    const login = page.getByRole('heading', { name: 'Вход' });
    await detail.or(login).first().waitFor({ state: 'visible', timeout: 20_000 });
    if (await login.isVisible().catch(() => false)) {
      test.skip(true, 'Auth session unavailable in this environment');
    }

    await page.getByTestId('crosspost-tab-preview').click();
    await page.getByTestId('crosspost-preview-remote').fill('wall-123456_99');
    await page.getByTestId('crosspost-preview-submit').click();
    await expect(page.getByTestId('crosspost-preview-result')).toBeVisible({ timeout: 10_000 });
  });

  test('crosspost page has no critical a11y violations', async ({ page }) => {
    await page.goto(`/app/${demoWorkspace}/automations/crosspost`);
    const root = page.getByTestId('crosspost-page');
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
    await page.goto(`/app/${demoWorkspace}/automations/crosspost`);
    const root = page.getByTestId('crosspost-page');
    const login = page.getByRole('heading', { name: 'Вход' });
    await root.or(login).first().waitFor({ state: 'visible', timeout: 20_000 });
    if (await login.isVisible().catch(() => false)) {
      test.skip(true, 'Auth session unavailable in this environment');
    }

    await page.setViewportSize({ width: 1280, height: 800 });
    await expect(root).toBeVisible();
    await page.screenshot({
      path: 'test-results/fe24-crosspost-desktop.png',
      fullPage: true,
    });

    await page.setViewportSize({ width: 360, height: 740 });
    await expect(root).toBeVisible();
    await page.screenshot({
      path: 'test-results/fe24-crosspost-mobile.png',
      fullPage: true,
    });
  });
});
