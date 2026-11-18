import type { Page } from '@playwright/test';

export class MediaPage {
  constructor(private readonly page: Page) {}

  async goto(workspaceId: string): Promise<void> {
    await this.page.goto(`/app/${workspaceId}/media`);
  }

  async expectLibraryHeading(): Promise<void> {
    await this.page.getByRole('heading', { name: 'Медиатека' }).waitFor({ state: 'visible' });
  }

  dropzone() {
    return this.page.getByTestId('file-dropzone');
  }

  grid() {
    return this.page.getByTestId('media-grid');
  }
}
