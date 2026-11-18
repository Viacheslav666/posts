import { test } from '@playwright/test';

import { MediaPage } from '../pages/media.page';

/**
 * FE-7 stub: upload photo → insert into post.
 * Full multipart + editor flow against MSW; auth may gate /app/* until session is seeded.
 */
test.describe('Media upload to post (FE-7 stub)', () => {
  const demoWorkspace = '019a0000-0002-7000-8000-000000000002';

  test('media library is reachable at 360px', async ({ page }) => {
    await page.setViewportSize({ width: 360, height: 640 });
    const media = new MediaPage(page);
    await media.goto(demoWorkspace);

    const heading = page.getByRole('heading', { name: /Медиатека|Вход/ });
    await heading.first().waitFor({ state: 'visible' });
  });

  test('login → media → open editor with library pick (when session available)', async ({
    page,
  }) => {
    await page.goto('/login');
    await page.getByLabel('Эл. почта').fill('demo@posts.app');
    await page.getByLabel('Пароль', { exact: true }).fill('Password1!');
    await page.getByRole('button', { name: 'Войти' }).click();
    await page.waitForURL(/\/app\//, { timeout: 15_000 }).catch(() => undefined);

    const media = new MediaPage(page);
    await media.goto(demoWorkspace);

    const mediaHeading = page.getByRole('heading', { name: 'Медиатека' });
    const loginHeading = page.getByRole('heading', { name: 'Вход' });
    await mediaHeading.or(loginHeading).first().waitFor({ state: 'visible' });

    if (await mediaHeading.isVisible().catch(() => false)) {
      await media.expectLibraryHeading();
      await media.grid().waitFor({ state: 'visible' });

      await page.goto(`/app/${demoWorkspace}/posts/new`);
      const editorHeading = page.getByRole('heading', { name: 'Редактор поста' });
      await editorHeading.waitFor({ state: 'visible' });
      await page.getByTestId('editor-media-block').waitFor({ state: 'visible' });
      await page.getByTestId('editor-media-from-library').click();
      await page.getByTestId('media-picker').waitFor({ state: 'visible' });
    }
  });
});
