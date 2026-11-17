import { expect, type Page } from '@playwright/test';

/**
 * Page object for the public web liveness page (`GET /health`).
 */
export class HealthPage {
  constructor(private readonly page: Page) {}

  async goto(): Promise<void> {
    await this.page.goto('/health');
  }

  async expectOk(): Promise<void> {
    await expect(this.page.getByRole('heading', { name: 'Health' })).toBeVisible();
    await expect(this.page.getByText('status: ok')).toBeVisible();
  }
}
