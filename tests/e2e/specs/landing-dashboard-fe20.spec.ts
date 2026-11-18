import { expect, test } from '@playwright/test';

/**
 * FE-20: landing sections, mobile login access, dashboard upcoming without skeletons.
 * Uses MSW via next dev (NEXT_PUBLIC_API_MOCKING=true). Does not freeze Date.
 */
test.describe('FE-20 landing & dashboard', () => {
  const demoWorkspace = '019a0000-0002-7000-8000-000000000002';

  test('landing renders all sections', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByTestId('landing-hero')).toBeVisible();
    await expect(page.getByTestId('landing-networks')).toBeVisible();
    await expect(page.getByTestId('landing-features')).toBeVisible();
    await expect(page.getByTestId('landing-how')).toBeVisible();
    await expect(page.getByTestId('landing-pricing')).toBeVisible();
    await expect(page.getByTestId('landing-faq')).toBeVisible();
    await expect(page.getByTestId('landing-final-cta')).toBeVisible();
    await expect(page.getByTestId('landing-footer')).toBeVisible();
    await expect(page.getByRole('link', { name: 'Оферта' })).toBeVisible();
    await expect(page.getByText('Instagram', { exact: false })).toHaveCount(0);
  });

  test('mobile header exposes login', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/');
    await expect(page.getByTestId('public-menu-trigger')).toBeVisible();
    await page.getByTestId('public-menu-trigger').click();
    await expect(page.getByTestId('public-mobile-menu')).toBeVisible();
    await expect(page.getByTestId('public-mobile-login')).toBeVisible();
    await page.getByTestId('public-mobile-login').click();
    await expect(page).toHaveURL(/\/login/);
  });

  test('dashboard upcoming shows data without perpetual skeleton', async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Эл. почта').fill('demo@posts.app');
    await page.getByLabel('Пароль', { exact: true }).fill('Password1!');
    await page.getByRole('button', { name: 'Войти' }).click();

    const loginHeading = page.getByRole('heading', { name: 'Вход' });
    const dashboard = page.getByTestId('dashboard-page');
    await dashboard.or(loginHeading).first().waitFor({ state: 'visible', timeout: 20_000 });
    if (await loginHeading.isVisible().catch(() => false)) {
      await page.goto(`/app/${demoWorkspace}`);
    }

    await expect(page.getByTestId('dashboard-page')).toBeVisible({ timeout: 20_000 });
    await expect(page.getByTestId('dashboard-upcoming')).toBeVisible();

    // Must resolve to real content (not hang on skeletons). No Date.freeze.
    await expect(page.getByTestId('dashboard-upcoming-skeleton')).toHaveCount(0, {
      timeout: 15_000,
    });
    await expect(page.getByTestId('dashboard-upcoming').getByText('Утренний дайджест')).toBeVisible({
      timeout: 15_000,
    });
    // Story must not duplicate into upcoming
    await expect(
      page.getByTestId('dashboard-upcoming').getByText('Вечерняя история VK'),
    ).toHaveCount(0);
  });
});
