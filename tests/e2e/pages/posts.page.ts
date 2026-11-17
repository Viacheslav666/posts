import type { Page } from '@playwright/test';

export class PostsPage {
  constructor(private readonly page: Page) {}

  async goto(workspaceId: string): Promise<void> {
    await this.page.goto(`/app/${workspaceId}/posts`);
  }

  async gotoCalendar(workspaceId: string): Promise<void> {
    await this.page.goto(`/app/${workspaceId}/calendar`);
  }

  heading() {
    return this.page.getByRole('heading', { name: 'Отложенные посты' });
  }

  calendarHeading() {
    return this.page.getByRole('heading', { name: 'Календарь' });
  }
}
