import { type Page } from '@playwright/test';

export class AnalyticsPage {
  constructor(private readonly page: Page) {}

  async loginDemo() {
    await this.page.goto('/login');
    await this.page.getByLabel('Эл. почта').fill('demo@posts.app');
    await this.page.getByLabel('Пароль', { exact: true }).fill('Password1!');
    await this.page.getByRole('button', { name: 'Войти' }).click();
    await this.page.waitForURL(/\/app\//, { timeout: 15_000 }).catch(() => undefined);
  }

  async gotoAnalytics(workspaceId: string, year?: number) {
    const query = year ? `?year=${year}` : '';
    await this.page.goto(`/app/${workspaceId}/analytics${query}`);
  }

  root() {
    return this.page.getByTestId('analytics-page');
  }

  yearSelect() {
    return this.page.getByTestId('analytics-year-select');
  }

  volumeChart() {
    return this.page.getByTestId('analytics-volume-chart');
  }

  errorsTable() {
    return this.page.getByTestId('analytics-errors-table');
  }

  retryButton() {
    return this.page.getByTestId('analytics-error-retry').first();
  }

  openErrorLink() {
    return this.page.getByTestId('analytics-error-open').first();
  }
}
