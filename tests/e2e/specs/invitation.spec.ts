import { test } from '@playwright/test';

import { InvitePage } from '../pages/invite.page';

/**
 * FE-5 stub: invitation preview against MSW (public, no login required for preview).
 */
test.describe('Invitation (FE-5 stub)', () => {
  test('loads invite page with demo token and shows Russian title', async ({ page }) => {
    const invite = new InvitePage(page);
    await invite.goto('demo-invite-token');
    await invite.expectLoaded();
    await invite.expectPreviewOrLoginCta();
  });

  test('invite page is usable at 360px mobile width', async ({ page }) => {
    await page.setViewportSize({ width: 360, height: 640 });
    const invite = new InvitePage(page);
    await invite.goto('demo-invite-token');
    await invite.expectLoaded();
    await invite.expectNoHorizontalOverflow();
  });
});
