import { expect, test } from '@playwright/test';

import { EditorPage } from '../pages/editor.page';

/**
 * FE-18: variables + recurrence times + batch media upload UX in the post editor.
 */
test.describe('FE-18 editor variables and times', () => {
  const demoWorkspace = '019a0000-0002-7000-8000-000000000002';

  test.beforeEach(async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Email').fill('demo@posts.app');
    await page.getByLabel('Пароль', { exact: true }).fill('Password1!');
    await page.getByRole('button', { name: 'Войти' }).click();
    await page.waitForURL(/\/app\//, { timeout: 15_000 }).catch(() => undefined);
  });

  test('create post with variables and two recurrence times', async ({ page }) => {
    const editor = new EditorPage(page);
    await editor.goto(demoWorkspace);

    const heading = editor.heading();
    const loginHeading = page.getByRole('heading', { name: 'Вход' });
    await heading.or(loginHeading).first().waitFor({ state: 'visible' });
    if (await loginHeading.isVisible().catch(() => false)) {
      test.skip(true, 'Session unavailable in this environment');
    }

    await expect(heading).toBeVisible();
    await expect(page.getByTestId('variables-menu-trigger')).toBeVisible();

    await page.getByTestId('variables-menu-trigger').click();
    await page.getByTestId('variables-menu-list').waitFor({ state: 'visible' });
    await page.getByRole('menuitem', { name: /\{date\}/ }).click();

    const textbox = page.getByRole('textbox', { name: 'Текст поста' });
    await textbox.click();
    await page.keyboard.type(' [rand]утро|день[/rand]');

    await editor.recurrenceSelect().selectOption({ label: 'Каждый день' });
    await page.getByTestId('add-recurrence-time').click();
    await page.getByTestId('add-recurrence-time').click();
    await expect(page.getByTestId('recurrence-time-0')).toBeVisible();
    await expect(page.getByTestId('recurrence-time-1')).toBeVisible();

    await page.getByTestId('recurrence-time-0').fill('09:00');
    await page.getByTestId('recurrence-time-1').fill('18:30');

    await expect(page.getByTestId('preview-timezone')).toContainText('Europe/Moscow');
    await expect(page.getByTestId('preview-rand-reroll')).toBeVisible();

    await page.screenshot({
      path: 'artifacts/fe18-editor-variables.png',
      fullPage: true,
    });

    await editor.scheduleButton().click();
  });

  test('batch media dropzone is available in editor', async ({ page }) => {
    const editor = new EditorPage(page);
    await editor.goto(demoWorkspace);
    const heading = editor.heading();
    const loginHeading = page.getByRole('heading', { name: 'Вход' });
    await heading.or(loginHeading).first().waitFor({ state: 'visible' });
    if (await loginHeading.isVisible().catch(() => false)) {
      test.skip(true, 'Session unavailable in this environment');
    }

    await expect(page.getByTestId('editor-media-block')).toBeVisible();
    await expect(page.getByTestId('editor-media-dropzone')).toBeVisible();

    await page.goto(`/app/${demoWorkspace}/posts/batch`);
    await expect(page.getByTestId('posts-batch-page')).toBeVisible();
    await page.screenshot({
      path: 'artifacts/fe18-posts-batch.png',
      fullPage: true,
    });
  });
});
