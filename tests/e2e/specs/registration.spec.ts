import { test } from '@playwright/test';

import { RegisterPage } from '../pages/register.page';

/**
 * FE-2 stub: registration happy-path against MSW mocks.
 * Expanded coverage lives in QA-4 / auth e2e epics.
 */
test.describe('Registration (FE-2 stub)', () => {
  test('fills the register form and reaches verify-email', async ({ page }) => {
    const register = new RegisterPage(page);
    await register.goto();
    await register.expectFormVisible();
    await register.fillCredentials({
      name: 'Playwright User',
      email: 'playwright@posts.app',
      password: 'Secret12',
    });
    await register.submit();
    await register.expectVerifyEmail('playwright@posts.app');
  });
});
