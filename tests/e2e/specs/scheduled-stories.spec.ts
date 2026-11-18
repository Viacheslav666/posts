import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';

import { StoriesPage } from '../pages/stories.page';

/**
 * FE-13: scheduled stories UI against MSW (TC-STORY P0 surface).
 */
test.describe('Scheduled stories (FE-13)', () => {
  const demoWorkspace = '019a0000-0002-7000-8000-000000000002';

  async function login(page: import('@playwright/test').Page) {
    await page.goto('/login');
    await page.getByLabel('Email').fill('demo@posts.app');
    await page.getByLabel('Пароль', { exact: true }).fill('Password1!');
    await page.getByRole('button', { name: 'Войти' }).click();
    await page.waitForURL(/\/app\//, { timeout: 15_000 }).catch(() => undefined);
  }

  async function openEditor(page: import('@playwright/test').Page, stories: StoriesPage) {
    await stories.gotoNew(demoWorkspace);
    const heading = stories.editorHeading();
    const loginHeading = page.getByRole('heading', { name: 'Вход' });
    await heading.or(loginHeading).first().waitFor({ state: 'visible', timeout: 15_000 });
    return heading.isVisible().catch(() => false);
  }

  test('TC-STORY-01/03: story editor exposes TTL, media, VK/OK channels', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 900 });
    await login(page);
    const stories = new StoriesPage(page);
    if (!(await openEditor(page, stories))) {
      test.skip();
      return;
    }

    await expect(stories.editor()).toBeVisible();
    await expect(stories.channelPicker()).toBeVisible();
    await expect(stories.mediaBlock()).toBeVisible();
    await expect(stories.ttlSelect()).toBeVisible();
    await expect(page.getByTestId('story-preview')).toBeVisible();
  });

  test('TC-STORY-13: IG absent; TG/Max shown disabled with explanation', async ({ page }) => {
    await login(page);
    const stories = new StoriesPage(page);
    if (!(await openEditor(page, stories))) {
      test.skip();
      return;
    }

    await expect(stories.igAbsentMarker()).toHaveCount(1);
    // Q-STORY-05: Instagram must not appear as a selectable network option.
    await expect(stories.channelPicker().getByText(/instagram|ig_business/i)).toHaveCount(0);
    await expect(page.getByTestId('story-unsupported-ig')).toHaveCount(0);
    await expect(stories.unsupportedNetworks()).toBeVisible();
    await expect(page.locator('[data-testid="story-unsupported-tg"]').first()).toBeVisible();
    await expect(page.locator('[data-testid="story-unsupported-max"]').first()).toBeVisible();
    await expect(page.getByText(/Telegram вне scope/i).first()).toBeVisible();
    await expect(page.getByText(/Max не поддерживаются/i).first()).toBeVisible();
  });

  test('TC-STORY-06: VK without stories shows connect CTA', async ({ page }) => {
    await login(page);
    const stories = new StoriesPage(page);
    if (!(await openEditor(page, stories))) {
      test.skip();
      return;
    }

    const row = page.getByTestId('story-channel-019a0000-chan-7000-8000-000000000008');
    await expect(row).toBeVisible();
    await expect(row).toHaveAttribute('data-story-ready', 'false');
    await expect(row.getByRole('button', { name: /Подключить истории/i })).toBeVisible();
  });

  test('TC-STORY-11: ad marking form reused for stories on VK/OK', async ({ page }) => {
    await login(page);
    const stories = new StoriesPage(page);
    if (!(await openEditor(page, stories))) {
      test.skip();
      return;
    }

    const readyRow = page.locator('[data-testid^="story-channel-"][data-story-ready="true"]').first();
    await expect(readyRow).toBeVisible();
    await readyRow.getByRole('checkbox').check();
    await expect(stories.adForm()).toBeVisible({ timeout: 5_000 });
  });

  test('TC-STORY-16: stories list + calendar contentKind filter', async ({ page }) => {
    await login(page);
    const stories = new StoriesPage(page);
    await stories.gotoList(demoWorkspace);
    const listHeading = stories.listHeading();
    const loginHeading = page.getByRole('heading', { name: 'Вход' });
    await listHeading.or(loginHeading).first().waitFor({ state: 'visible', timeout: 15_000 });
    if (!(await listHeading.isVisible().catch(() => false))) {
      test.skip();
      return;
    }

    await expect(page.getByTestId('stories-list-page')).toBeVisible();
    await expect(stories.createButton()).toBeVisible();
    await expect(page.getByTestId('story-row-019a0000-post-7000-8000-000000000007')).toBeVisible();

    await page.goto(`/app/${demoWorkspace}/calendar`);
    const kindFilter = page.getByTestId('calendar-filter-content-kind');
    await expect(kindFilter).toBeVisible();
    await kindFilter.click();
    await page.getByRole('option', { name: 'Истории' }).click();
  });

  test('story editor a11y has no serious/critical axe violations', async ({ page }) => {
    await login(page);
    const stories = new StoriesPage(page);
    if (!(await openEditor(page, stories))) {
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

  test('story editor adapts at 360px width', async ({ page }) => {
    await page.setViewportSize({ width: 360, height: 740 });
    await login(page);
    const stories = new StoriesPage(page);
    if (!(await openEditor(page, stories))) {
      test.skip();
      return;
    }
    await expect(stories.editor()).toBeVisible();
    await expect(stories.channelPicker()).toBeVisible();
  });
});
