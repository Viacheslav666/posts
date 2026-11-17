import { expect, test } from '@playwright/test';

/**
 * FE-2 stub: registration happy-path against MSW mocks.
 * QA expands coverage in QA-2 / QA-4.
 */
test.describe('Registration (FE-2 stub)', () => {
  test('fills the register form and reaches verify-email', async ({ page }) => {
    await page.goto('/register');

    await expect(page.getByRole('heading', { name: 'Регистрация' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'VK ID' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Яндекс ID' })).toBeVisible();

    await page.getByLabel('Имя').fill('Playwright User');
    await page.getByLabel('Email').fill('playwright@posts.app');
    await page.getByLabel('Пароль').fill('Secret12');
    await page.getByLabel('Повтор пароля').fill('Secret12');
    await page.getByRole('checkbox').check();
    await page.getByTestId('register-submit').click();

    await expect(page).toHaveURL(/\/verify-email\?email=playwright%40posts\.app/);
    await expect(page.getByRole('heading', { name: 'Подтверждение email' })).toBeVisible();
  });
});
