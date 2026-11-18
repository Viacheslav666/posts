import type { Page } from '@playwright/test';

export class EditorPage {
  constructor(private readonly page: Page) {}

  async goto(workspaceId: string): Promise<void> {
    await this.page.goto(`/app/${workspaceId}/posts/new`);
  }

  heading() {
    return this.page.getByRole('heading', { name: 'Редактор поста' });
  }

  optionsPanel() {
    return this.page.getByTestId('post-options-panel');
  }

  adForm() {
    return this.page.getByTestId('ad-marking-form');
  }

  adToggle() {
    return this.page.getByTestId('ad-is-ad-toggle');
  }

  scheduleButton() {
    return this.page.getByTestId('editor-schedule');
  }

  recurrenceSelect() {
    return this.page.getByTestId('recurrence-select');
  }
}
