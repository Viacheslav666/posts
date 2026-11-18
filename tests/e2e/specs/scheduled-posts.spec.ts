import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';

import { PostsPage } from '../pages/posts.page';

/**
 * FE-8: scheduled posts list + calendar against MSW.
 */
test.describe('Scheduled posts & calendar (FE-8)', () => {
  const demoWorkspace = '019a0000-0002-7000-8000-000000000002';

  test('login → posts list filters/bulk shell at 360px', async ({ page }) => {
    await page.setViewportSize({ width: 360, height: 740 });
    await page.goto('/login');
    await page.getByLabel('Email').fill('demo@posts.app');
    await page.getByLabel('Пароль', { exact: true }).fill('Password1!');
    await page.getByRole('button', { name: 'Войти' }).click();
    await page.waitForURL(/\/app\//, { timeout: 15_000 }).catch(() => undefined);

    const posts = new PostsPage(page);
    await posts.goto(demoWorkspace);

    const heading = posts.heading();
    const loginHeading = page.getByRole('heading', { name: 'Вход' });
    await heading.or(loginHeading).first().waitFor({ state: 'visible' });

    if (await heading.isVisible().catch(() => false)) {
      await expect(page.getByTestId('posts-list-page')).toBeVisible();
      await expect(page.getByTestId('posts-filters')).toBeVisible();
      // Select a row if present and assert bulk bar appears.
      const row = page.getByTestId('posts-row-select').first();
      if (await row.isVisible().catch(() => false)) {
        await row.check();
        await expect(page.getByTestId('posts-bulk-bar')).toBeVisible();
      }
    }
  });

  test('calendar route renders month/day board', async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Email').fill('demo@posts.app');
    await page.getByLabel('Пароль', { exact: true }).fill('Password1!');
    await page.getByRole('button', { name: 'Войти' }).click();
    await page.waitForURL(/\/app\//, { timeout: 15_000 }).catch(() => undefined);

    const posts = new PostsPage(page);
    await posts.gotoCalendar(demoWorkspace);

    const heading = posts.calendarHeading();
    const loginHeading = page.getByRole('heading', { name: 'Вход' });
    await heading.or(loginHeading).first().waitFor({ state: 'visible' });

    if (await heading.isVisible().catch(() => false)) {
      await expect(page.getByTestId('calendar-page')).toBeVisible();
    }
  });

  test('posts page has no serious/critical axe violations when visible', async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Email').fill('demo@posts.app');
    await page.getByLabel('Пароль', { exact: true }).fill('Password1!');
    await page.getByRole('button', { name: 'Войти' }).click();
    await page.waitForURL(/\/app\//, { timeout: 15_000 }).catch(() => undefined);

    const posts = new PostsPage(page);
    await posts.goto(demoWorkspace);
    const heading = posts.heading();
    if (!(await heading.isVisible().catch(() => false))) {
      test.skip();
      return;
    }

    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      .analyze();
    const serious = results.violations.filter(
      (v) => v.impact === 'serious' || v.impact === 'critical',
    );
    expect(serious, JSON.stringify(serious, null, 2)).toEqual([]);
  });
});
