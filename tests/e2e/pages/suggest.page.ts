import type { Locator, Page } from '@playwright/test';

export class SuggestPage {
  constructor(private readonly page: Page) {}

  async goto(workspaceId: string): Promise<void> {
    await this.page.goto(`/app/${workspaceId}/automations/suggest`);
  }

  async gotoSettings(workspaceId: string, settingsId: string): Promise<void> {
    await this.page.goto(`/app/${workspaceId}/automations/suggest/${settingsId}`);
  }

  root(): Locator {
    return this.page.getByTestId('suggest-page');
  }

  detail(): Locator {
    return this.page.getByTestId('suggest-settings-detail');
  }

  heading(): Locator {
    return this.page.getByRole('heading', { name: 'Предложка' });
  }

  settingsCard(settingsId: string): Locator {
    return this.page.getByTestId(`suggest-settings-${settingsId}`);
  }

  openSettings(settingsId: string): Locator {
    return this.page.getByTestId(`suggest-open-${settingsId}`);
  }

  queue(): Locator {
    return this.page.getByTestId('suggest-moderation-queue');
  }

  settingsForm(): Locator {
    return this.page.getByTestId('suggest-settings-form');
  }

  settingsTab(): Locator {
    return this.page.getByTestId('suggest-tab-settings');
  }

  queueTab(): Locator {
    return this.page.getByTestId('suggest-tab-queue');
  }

  approveButton(itemId: string): Locator {
    return this.page.getByTestId(`suggest-approve-${itemId}`);
  }

  bulkApproveAll(): Locator {
    return this.page.getByTestId('suggest-bulk-approve-all');
  }

  bulkProgress(): Locator {
    return this.page.getByTestId('suggest-bulk-progress');
  }

  saveSettings(): Locator {
    return this.page.getByTestId('suggest-settings-save');
  }

  stopWordsEditor(): Locator {
    return this.page.locator('#suggest-stop-words');
  }
}
