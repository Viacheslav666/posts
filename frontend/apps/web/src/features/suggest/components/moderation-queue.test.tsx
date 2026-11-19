import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  getSuggestItem,
  ITEM_IDS,
  listSuggestItems,
  resetSuggestStore,
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
