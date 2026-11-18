import { expect, type Page } from '@playwright/test';

export class NotificationsPage {
  constructor(private readonly page: Page) {}

  async loginDemo() {
    await this.page.goto('/login');
    await this.page.getByLabel('Эл. почта').fill('demo@posts.app');
    await this.page.getByLabel('Пароль', { exact: true }).fill('Password1!');
    await this.page.getByRole('button', { name: 'Войти' }).click();
    await this.page.waitForURL(/\/app\//, { timeout: 15_000 }).catch(() => undefined);
  }

  async gotoDashboard(workspaceId: string) {
    await this.page.goto(`/app/${workspaceId}`);
  }

  async gotoNotifications(workspaceId: string) {
    await this.page.goto(`/app/${workspaceId}/notifications`);
  }

  async gotoNotificationSettings(workspaceId: string) {
    await this.page.goto(`/app/${workspaceId}/settings/notifications`);
  }

  bell() {
    return this.page.getByTestId('notifications-bell');
  }

  unreadCount() {
    return this.page.getByTestId('notifications-unread-count');
  }

  panel() {
    return this.page.getByTestId('notifications-panel');
  }

  async openBell() {
    await this.bell().click();
    await expect(this.panel()).toBeVisible();
  }

  dashboard() {
    return this.page.getByTestId('dashboard-page');
  }

  settingsForm() {
    return this.page.getByTestId('notification-settings-save');
  }
}
