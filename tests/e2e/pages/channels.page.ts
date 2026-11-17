import { expect, type Page } from '@playwright/test';

/**
 * Page object for Channels (A7) — TG connect stub against MSW.
 */
export class ChannelsPage {
  constructor(private readonly page: Page) {}

  async goto(workspaceId: string): Promise<void> {
    await this.page.goto(`/app/${workspaceId}/channels`);
  }

  async expectLoaded(): Promise<void> {
    await expect(this.page.getByRole('heading', { name: 'Каналы' })).toBeVisible();
  }

  async openTelegramWizard(): Promise<void> {
    const connectTab = this.page.getByRole('tab', { name: 'Подключить' });
    if (await connectTab.isVisible().catch(() => false)) {
      await connectTab.click();
    }
    const networkCard = this.page.getByTestId('connect-network-tg');
    if (await networkCard.isVisible().catch(() => false)) {
      await networkCard.click();
      return;
    }
    await this.page.getByTestId('connect-tg').click();
  }

  async expectTelegramWizard(): Promise<void> {
    await expect(this.page.getByTestId('bot-wizard-tg')).toBeVisible();
  }

  async expectNoHorizontalOverflow(): Promise<void> {
    const heading = this.page.getByRole('heading', { name: 'Каналы' });
    await expect(heading).toBeVisible();
    const box = await heading.boundingBox();
    const viewport = this.page.viewportSize();
    expect(box).toBeTruthy();
    expect(viewport).toBeTruthy();
    if (box && viewport) {
      expect(box.x + box.width).toBeLessThanOrEqual(viewport.width + 1);
    }
  }
}
