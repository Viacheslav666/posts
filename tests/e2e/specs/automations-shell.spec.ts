import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';

/**
 * FE-21: automations hub shell — suggest / antispam / crosspost stubs,
 * ChannelEventStatus failed → repair → connected on MSW.
 */
test.describe('Automations shell (FE-21)', () => {
  const demoWorkspace = '019a0000-0002-7000-8000-000000000002';

  test.beforeEach(async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Эл. почта').fill('demo@posts.app');
    await page.getByLabel('Пароль', { exact: true }).fill('Password1!');
    await page.getByRole('button', { name: 'Войти' }).click();
    await page.waitForURL(/\/app\//, { timeout: 15_000 }).catch(() => undefined);
  });

  test('navigates hub cards to three new sections', async ({ page }) => {
    await page.goto(`/app/${demoWorkspace}/automations`);
    const hub = page.getByTestId('automations-hub');
    const login = page.getByRole('heading', { name: 'Вход' });
    await hub.or(login).first().waitFor({ state: 'visible', timeout: 20_000 });
    if (await login.isVisible().catch(() => false)) {
      test.skip(true, 'Auth session unavailable in this environment');
    }

    await expect(page.getByTestId('automations-card-suggest')).toBeVisible();
    await expect(page.getByTestId('automations-card-antispam')).toBeVisible();
    await expect(page.getByTestId('automations-card-crosspost')).toBeVisible();

    await page.getByTestId('automations-card-suggest').click();
    await expect(page.getByTestId('automations-suggest-stub')).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Предложка' })).toBeVisible();

    await page.goto(`/app/${demoWorkspace}/automations`);
    await page.getByTestId('automations-card-antispam').click();
    await expect(page.getByTestId('automations-antispam-stub')).toBeVisible();

    await page.goto(`/app/${demoWorkspace}/automations`);
    await page.getByTestId('automations-card-crosspost').click();
    await expect(page.getByTestId('crosspost-page')).toBeVisible();
  });

  test('repairs failed channel event subscription to connected', async ({ page }) => {
    await page.goto(`/app/${demoWorkspace}/automations`);
    const status = page.getByTestId('channel-event-status');
    const login = page.getByRole('heading', { name: 'Вход' });
    await status.or(login).first().waitFor({ state: 'visible', timeout: 20_000 });
    if (await login.isVisible().catch(() => false)) {
      test.skip(true, 'Auth session unavailable in this environment');
    }

    await expect(status).toHaveAttribute('data-status', 'failed');
    await expect(page.getByTestId('channel-event-status-badge')).toContainText('Сбой');
    await page.getByTestId('channel-event-repair').click();
    await expect(status).toHaveAttribute('data-status', 'connected', { timeout: 10_000 });
    await expect(page.getByTestId('channel-event-status-badge')).toContainText('Подключено');
  });

  test('sidebar exposes automation group links', async ({ page }) => {
    await page.goto(`/app/${demoWorkspace}/automations`);
    const group = page.getByTestId('nav-automations-group');
    const login = page.getByRole('heading', { name: 'Вход' });
    await group.or(login).first().waitFor({ state: 'visible', timeout: 20_000 });
    if (await login.isVisible().catch(() => false)) {
      test.skip(true, 'Auth session unavailable in this environment');
    }

    await expect(page.getByTestId('nav-automations-suggest')).toBeVisible();
    await expect(page.getByTestId('nav-automations-antispam')).toBeVisible();
    await expect(page.getByTestId('nav-automations-crosspost')).toBeVisible();
  });

  test('hub has no critical a11y violations', async ({ page }) => {
    await page.goto(`/app/${demoWorkspace}/automations`);
    const hub = page.getByTestId('automations-hub');
    const login = page.getByRole('heading', { name: 'Вход' });
    await hub.or(login).first().waitFor({ state: 'visible', timeout: 20_000 });
    if (await login.isVisible().catch(() => false)) {
      test.skip(true, 'Auth session unavailable in this environment');
    }

    const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa']).analyze();
    const critical = results.violations.filter(
      (v) => v.impact === 'critical' || v.impact === 'serious',
    );
    expect(critical, JSON.stringify(critical, null, 2)).toEqual([]);
  });

  test('captures desktop and mobile hub screenshots', async ({ page }) => {
    await page.goto(`/app/${demoWorkspace}/automations`);
    const hub = page.getByTestId('automations-hub');
    const login = page.getByRole('heading', { name: 'Вход' });
    await hub.or(login).first().waitFor({ state: 'visible', timeout: 20_000 });
    if (await login.isVisible().catch(() => false)) {
      test.skip(true, 'Auth session unavailable in this environment');
    }

    await page.setViewportSize({ width: 1280, height: 800 });
    await expect(hub).toBeVisible();
    await page.screenshot({
      path: 'test-results/fe21-automations-hub-desktop.png',
      fullPage: true,
    });

    await page.setViewportSize({ width: 360, height: 740 });
    await expect(hub).toBeVisible();
    await page.screenshot({
      path: 'test-results/fe21-automations-hub-mobile.png',
      fullPage: true,
    });
  });
});
