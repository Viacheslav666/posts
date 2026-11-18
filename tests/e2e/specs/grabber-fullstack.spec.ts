import { expect, test } from '@playwright/test';

import { apiBaseUrl } from '../helpers/api';
import { GrabberPage } from '../pages/grabber.page';

/**
 * QA-12 full-stack smoke (compose profile `full` + social-emulator wall fixtures).
 * Skips unless E2E_GRABBER_FULLSTACK=1 (nightly). MSW path remains in grabber-moderation.spec.ts.
 */
const fullstack = process.env.E2E_GRABBER_FULLSTACK === '1';

test.describe('Grabber full-stack (QA-12)', () => {
  test.skip(!fullstack, 'Set E2E_GRABBER_FULLSTACK=1 against compose full');

  const emulatorBase = process.env.EMULATOR_BASE_URL ?? 'http://127.0.0.1:8090';
  const api = apiBaseUrl().replace(/\/$/, '');

  test('create rule → ingest → queue → approve → publication linkage', async ({ request, page }) => {
    const email = `grab-e2e-${Date.now()}@example.com`;
    const password = 'Password1!Password1!';

    const register = await request.post(`${api}/api/v1/auth/register`, {
      data: { email, password, name: 'Grab E2E' },
    });
    expect(register.status(), await register.text()).toBe(201);

    // openapi/local may auto-verify; otherwise skip if verify mail not available
    const login = await request.post(`${api}/api/v1/auth/login`, {
      data: { email, password },
    });
    if (login.status() !== 200) {
      test.skip(true, 'Email verification required; use seeded demo account in nightly');
    }
    const tokens = await login.json();
    const accessToken = tokens.accessToken as string;

    const workspaces = await request.get(`${api}/api/v1/workspaces`, {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    expect(workspaces.ok()).toBeTruthy();
    const workspaceId = (await workspaces.json()).items[0].id as string;

    const projectRes = await request.post(`${api}/api/v1/workspaces/${workspaceId}/projects`, {
      headers: { Authorization: `Bearer ${accessToken}` },
      data: { name: 'Grab E2E', color: '#224466' },
    });
    expect(projectRes.status(), await projectRes.text()).toBe(201);
    const projectId = (await projectRes.json()).id as string;

    // Seed VK wall in emulator (ingest may use WireMock in IT; here emulator is the source when configured).
    await request.put(`${emulatorBase}/_control/vk/wall/-123`, {
      data: {
        items: [{ id: 501, text: 'E2E grabbed post', marked_as_ads: 0 }],
      },
    });

    // Channel seed is environment-specific; if channels API is unavailable, stop early.
    const channels = await request.get(
      `${api}/api/v1/workspaces/${workspaceId}/projects/${projectId}/channels`,
      { headers: { Authorization: `Bearer ${accessToken}` } },
    );
    if (!channels.ok()) {
      test.skip(true, 'Channels API not available in this stack');
    }
    const channelItems = (await channels.json()).items ?? [];
    if (!channelItems.length) {
      test.skip(true, 'No VK channel in workspace — seed required for full-stack publish');
    }
    const channelId = channelItems[0].id as string;

    const ruleRes = await request.post(
      `${api}/api/v1/workspaces/${workspaceId}/projects/${projectId}/ingest/rules`,
      {
        headers: { Authorization: `Bearer ${accessToken}` },
        data: {
          type: 'grabber',
          name: 'E2E Grabber',
          targetChannelId: channelId,
          userResponsibilityAccepted: true,
          activate: true,
          config: {
            sourceIds: ['-123'],
            scanIntervalMinutes: 15,
            publishMode: 'moderate',
          },
        },
      },
    );
    expect(ruleRes.status(), await ruleRes.text()).toBe(201);
    const rule = await ruleRes.json();
    expect(rule.config.publishMode).toBe('moderate');

    const runRes = await request.post(`${api}/api/v1/ingest/rules/${rule.id}/runs`, {
      headers: {
        Authorization: `Bearer ${accessToken}`,
        'Idempotency-Key': `e2e-run-${Date.now()}`,
      },
    });
    expect([202, 200].includes(runRes.status()), await runRes.text()).toBeTruthy();

    const itemsRes = await request.get(`${api}/api/v1/ingest/rules/${rule.id}/items?status=pending_moderation`, {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    expect(itemsRes.ok()).toBeTruthy();
    const items = (await itemsRes.json()).items ?? [];
    expect(items.length).toBeGreaterThanOrEqual(1);
    const itemId = items[0].id as string;

    // UI queue visible when web is up with real API (PLAYWRIGHT_SKIP_WEBSERVER=1).
    await page.goto(`/login`);
    await page.getByLabel('Email').fill(email);
    await page.getByLabel('Пароль', { exact: true }).fill(password);
    await page.getByRole('button', { name: 'Войти' }).click();
    await page.waitForURL(/\/app\//, { timeout: 20_000 }).catch(() => undefined);

    const grabber = new GrabberPage(page);
    await grabber.goto(workspaceId);
    if (await page.getByRole('heading', { name: 'Граббер' }).isVisible().catch(() => false)) {
      await expect(grabber.queue()).toBeVisible();
    }

    const approveRes = await request.post(`${api}/api/v1/ingest/items/${itemId}/approve`, {
      headers: {
        Authorization: `Bearer ${accessToken}`,
        'Idempotency-Key': `e2e-approve-${itemId}`,
      },
      data: {},
    });
    expect(approveRes.ok(), await approveRes.text()).toBeTruthy();
    const approved = await approveRes.json();
    expect(approved.postId).toBeTruthy();

    // Publication exists in pipeline (pending until publisher consumes).
    const itemDetail = await request.get(`${api}/api/v1/ingest/items/${itemId}`, {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    expect(itemDetail.ok()).toBeTruthy();
    const detail = await itemDetail.json();
    expect(detail.postId).toBeTruthy();
    expect(['scheduled', 'published', 'approved']).toContain(detail.status);
  });
});
