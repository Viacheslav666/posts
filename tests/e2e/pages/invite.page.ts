import { expect, type Page } from '@playwright/test';

/**
 * Page object for invitation accept (`/invite?token=...`).
 */
export class InvitePage {
  constructor(private readonly page: Page) {}

  async goto(token = 'demo-invite-token'): Promise<void> {
    await this.page.goto(`/invite?token=${encodeURIComponent(token)}`);
  }

  async expectLoaded(): Promise<void> {
    await expect(this.page.getByRole('heading', { name: 'Приглашение в команду' })).toBeVisible();
  }

  async expectPreviewOrLoginCta(): Promise<void> {
    const preview = this.page.getByTestId('invite-preview');
    const loginCta = this.page.getByRole('link', { name: 'Войти' });
    await expect(preview.or(loginCta)).toBeVisible();
  }

  async expectNoHorizontalOverflow(): Promise<void> {
    const heading = this.page.getByRole('heading', { name: 'Приглашение в команду' });
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
