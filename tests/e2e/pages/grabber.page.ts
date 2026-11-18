import type { Page } from '@playwright/test';

export class GrabberPage {
  constructor(private readonly page: Page) {}

  async goto(workspaceId: string) {
    await this.page.goto(`/app/${workspaceId}/automations/grabber`);
  }

  async gotoNew(workspaceId: string) {
    await this.page.goto(`/app/${workspaceId}/automations/grabber/new`);
  }

  heading() {
    return this.page.getByRole('heading', { name: 'Граббер' });
  }

  queue() {
    return this.page.getByTestId('grabber-moderation-queue');
  }

  createRuleButton() {
    return this.page.getByTestId('grabber-create-rule');
  }

  approveButton(itemId: string) {
    return this.page.getByTestId(`grabber-approve-${itemId}`);
  }
}
