import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { CHANNEL_IDS } from '@/mocks/channel-store';
import { server } from '@/mocks/server';
import {
  getSuggestItem,
  ITEM_IDS,
  listSuggestItems,
  resetSuggestStore,
  seedSuggestItems,
  SETTINGS_IDS,
} from '@/mocks/suggest-store';
import { PERSONAL_WORKSPACE_ID } from '@/mocks/workspace-store';
import { setAccessToken } from '@/shared/api-client';
import { renderWithProviders } from '@/test/render';

import { getSuggestSettings } from '@/mocks/suggest-store';

import { ModerationQueue } from './moderation-queue';

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), prefetch: vi.fn() }),
  useParams: () => ({ workspace: PERSONAL_WORKSPACE_ID }),
  usePathname: () => `/app/${PERSONAL_WORKSPACE_ID}/automations/suggest/${SETTINGS_IDS.vkNews}`,
}));

describe('ModerationQueue (FE-22)', () => {
  beforeEach(() => {
    resetSuggestStore();
    setAccessToken('msw.access.token');
    vi.spyOn(window, 'confirm').mockReturnValue(true);
  });

  it('approves a pending item', async () => {
    const user = userEvent.setup();
    const settings = getSuggestSettings(SETTINGS_IDS.vkNews)!;

    renderWithProviders(
      <ModerationQueue
        workspaceId={PERSONAL_WORKSPACE_ID}
        settings={settings}
        canModerate
        canManage
      />,
    );

    await waitFor(() => {
      expect(screen.getByTestId('suggest-moderation-queue')).toBeInTheDocument();
    });

    const approve = await screen.findByTestId(`suggest-approve-${ITEM_IDS.pending1}`);
    await user.click(approve);

    await waitFor(() => {
      expect(getSuggestItem(ITEM_IDS.pending1)?.status).toBe('published');
    });
  });

  it('bulk publishes all pending with progress UI', async () => {
    const user = userEvent.setup();
    const settings = getSuggestSettings(SETTINGS_IDS.vkNews)!;

    renderWithProviders(
      <ModerationQueue
        workspaceId={PERSONAL_WORKSPACE_ID}
        settings={settings}
        canModerate
        canManage
      />,
    );

    await waitFor(() => {
      expect(screen.getByTestId('suggest-bulk-approve-all')).toBeInTheDocument();
    });

    await user.click(screen.getByTestId('suggest-bulk-approve-all'));

    await waitFor(() => {
      expect(screen.getByTestId('suggest-bulk-progress')).toBeInTheDocument();
    });

    await waitFor(() => {
      expect(
        listSuggestItems(SETTINGS_IDS.vkNews, { status: 'pending_moderation' }),
      ).toHaveLength(0);
    });
  });

  it('loads the next page instead of stopping at 50 pending items', async () => {
    const user = userEvent.setup();
    const extra = Array.from({ length: 48 }, (_, index) => ({
      id: `019a0000-sitem-7000-8000-${String(index + 10).padStart(12, '0')}`,
      workspaceId: PERSONAL_WORKSPACE_ID,
      channelId: CHANNEL_IDS.vkNews,
      settingsId: SETTINGS_IDS.vkNews,
      vkPostId: String(500000 + index),
      authorVkId: '42',
      text: `extra ${index}`,
      status: 'pending_moderation' as const,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    }));
    seedSuggestItems(extra);
    const hiddenId = extra[extra.length - 1]!.id;
    const settings = getSuggestSettings(SETTINGS_IDS.vkNews)!;

    renderWithProviders(
      <ModerationQueue workspaceId={PERSONAL_WORKSPACE_ID} settings={settings} canModerate canManage />,
    );

    expect(await screen.findByTestId('suggest-queue-load-more')).toBeInTheDocument();
    expect(screen.queryByTestId(`suggest-item-${hiddenId}`)).not.toBeInTheDocument();

    await user.click(screen.getByTestId('suggest-queue-load-more'));

    expect(await screen.findByTestId(`suggest-item-${hiddenId}`)).toBeInTheDocument();
  });

  it('does not render an item that belongs to another workspace', async () => {
    const foreignId = '019a0000-sitem-7000-8000-000000000099';
    seedSuggestItems([
      {
        id: foreignId,
        workspaceId: '019a0000-0002-7000-8000-000000000099',
        channelId: CHANNEL_IDS.vkNews,
        settingsId: SETTINGS_IDS.vkNews,
        vkPostId: '9',
        authorVkId: '9',
        text: 'foreign',
        status: 'pending_moderation',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      },
    ]);
    const settings = getSuggestSettings(SETTINGS_IDS.vkNews)!;

    renderWithProviders(
      <ModerationQueue workspaceId={PERSONAL_WORKSPACE_ID} settings={settings} canModerate canManage />,
    );

    expect(await screen.findByTestId(`suggest-item-${ITEM_IDS.pending1}`)).toBeInTheDocument();
    expect(screen.queryByTestId(`suggest-item-${foreignId}`)).not.toBeInTheDocument();
  });

  it('shows a load error instead of an empty queue', async () => {
    server.use(
      http.get('/api/v1/suggest/settings/:settingsId/items', () =>
        HttpResponse.json({ title: 'fail', code: 'internal' }, { status: 500 }),
      ),
    );
    const settings = getSuggestSettings(SETTINGS_IDS.vkNews)!;

    renderWithProviders(
      <ModerationQueue workspaceId={PERSONAL_WORKSPACE_ID} settings={settings} canModerate canManage />,
    );

    expect(await screen.findByRole('alert')).toHaveTextContent('Не удалось загрузить очередь');
    expect(screen.queryByText('Очередь пуста')).not.toBeInTheDocument();
  });

  it('exposes sync last 100 control for managers', async () => {
    const settings = getSuggestSettings(SETTINGS_IDS.vkNews)!;
    renderWithProviders(
      <ModerationQueue
        workspaceId={PERSONAL_WORKSPACE_ID}
        settings={settings}
        canModerate
        canManage
      />,
    );
    expect(await screen.findByTestId('suggest-sync-last-100')).toBeInTheDocument();
  });
});
