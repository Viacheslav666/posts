import type { Page } from '@playwright/test';

export class OrdPage {
  constructor(private readonly page: Page) {}

  async goto(workspaceId: string): Promise<void> {
    await this.page.goto(`/app/${workspaceId}/ord`);
  }

  heading() {
    return this.page.getByRole('heading', { name: 'Реклама / ОРД' });
  }

  connectionForm() {
    return this.page.getByTestId('ord-connection-form');
  }

  apiKey() {
    return this.page.getByTestId('ord-api-key');
  }

  contractors() {
    return this.page.getByTestId('ord-contractors');
  }
}
