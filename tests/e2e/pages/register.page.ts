import { expect, type Page } from '@playwright/test';

/**
 * Page object for the public registration form (`/register`).
 * Used by the FE-2 stub scenario; expanded as auth e2e grows.
 */
export class RegisterPage {
  constructor(private readonly page: Page) {}

  async goto(): Promise<void> {
    await this.page.goto('/register');
  }

  async expectFormVisible(): Promise<void> {
    await expect(this.page.getByRole('heading', { name: 'Регистрация' })).toBeVisible();
    await expect(this.page.getByRole('button', { name: 'VK ID' })).toBeVisible();
    await expect(this.page.getByRole('button', { name: 'Яндекс ID' })).toBeVisible();
  }

  async fillCredentials(input: {
    name: string;
    email: string;
    password: string;
  }): Promise<void> {
    await this.page.getByLabel('Имя').fill(input.name);
    await this.page.getByLabel('Эл. почта').fill(input.email);
    await this.page.getByLabel('Пароль').fill(input.password);
    await this.page.getByLabel('Повтор пароля').fill(input.password);
    await this.page.getByRole('checkbox').check();
  }

  async submit(): Promise<void> {
    await this.page.getByTestId('register-submit').click();
  }

  async expectVerifyEmail(email: string): Promise<void> {
    const encoded = encodeURIComponent(email);
    await expect(this.page).toHaveURL(new RegExp(`/verify-email\\?email=${encoded}`));
    await expect(this.page.getByRole('heading', { name: 'Подтверждение email' })).toBeVisible();
  }
}
