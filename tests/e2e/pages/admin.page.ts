import { type Page } from '@playwright/test';

export class AdminPage {
  constructor(private readonly page: Page) {}

  async loginDemo() {
    await this.page.goto('/login');
    await this.page.getByLabel('Email').fill('demo@posts.app');
    await this.page.getByLabel('Пароль', { exact: true }).fill('Password1!');
    await this.page.getByRole('button', { name: 'Войти' }).click();
    await this.page.waitForURL(/\/app\//, { timeout: 15_000 }).catch(() => undefined);
  }

  async gotoUsers() {
    await this.page.goto('/admin/users');
  }

  async gotoQueues() {
    await this.page.goto('/admin/queues');
  }

  async gotoPromos() {
    await this.page.goto('/admin/promos');
  }

  async gotoAnnouncements() {
    await this.page.goto('/admin/announcements');
  }

  usersRoot() {
    return this.page.getByTestId('admin-users-page');
  }

  forbidden() {
    return this.page.getByTestId('admin-forbidden');
  }

  impersonationBanner() {
    return this.page.getByTestId('impersonation-banner');
  }
}
