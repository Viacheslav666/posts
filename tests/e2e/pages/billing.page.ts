import { type Page } from '@playwright/test';

export class BillingPage {
  constructor(private readonly page: Page) {}

  async loginDemo() {
    await this.page.goto('/login');
    await this.page.getByLabel('Эл. почта').fill('demo@posts.app');
    await this.page.getByLabel('Пароль', { exact: true }).fill('Password1!');
    await this.page.getByRole('button', { name: 'Войти' }).click();
    await this.page.waitForURL(/\/app\//, { timeout: 15_000 }).catch(() => undefined);
  }

  async gotoBilling(workspaceId: string) {
    await this.page.goto(`/app/${workspaceId}/billing`);
  }

  root() {
    return this.page.getByTestId('billing-page');
  }

  usage() {
    return this.page.getByTestId('billing-usage');
  }

  legalForm() {
    return this.page.getByTestId('billing-legal-form');
  }
}
