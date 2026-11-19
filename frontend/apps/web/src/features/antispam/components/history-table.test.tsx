import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { CHANNEL_IDS } from '@/mocks/channel-store';
import {
  LOG_IDS,
  RULE_IDS,
  listAntispamLog,
  resetAntispamStore,
} from '@/mocks/antispam-store';
import { PERSONAL_WORKSPACE_ID } from '@/mocks/workspace-store';
import { setAccessToken } from '@/shared/api-client';
import { renderWithProviders } from '@/test/render';

import { HistoryTable } from './history-table';

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), prefetch: vi.fn() }),
  useParams: () => ({ workspace: PERSONAL_WORKSPACE_ID }),
}));

const channels = [
  {
    id: CHANNEL_IDS.vkNews,
    workspaceId: PERSONAL_WORKSPACE_ID,
    network: 'vk' as const,
    title: 'Posts — новости',
    status: 'active' as const,
    type: 'group' as const,
  },
  {
    id: CHANNEL_IDS.tgNews,
    workspaceId: PERSONAL_WORKSPACE_ID,
    network: 'tg' as const,
    title: '@posts_news',
    status: 'active' as const,
    type: 'channel' as const,
  },
];

describe('HistoryTable (FE-23)', () => {
  beforeEach(() => {
    resetAntispamStore();
    setAccessToken('msw.access.token');
  });

  it('lists seeded hits and restores VK comment', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <HistoryTable
        workspaceId={PERSONAL_WORKSPACE_ID}
        channels={channels}
        canModerate
      />,
    );

    await waitFor(() => {
      expect(screen.getByTestId('antispam-history')).toBeInTheDocument();
    });
    expect(await screen.findByTestId(`antispam-history-item-${LOG_IDS.vkDeleted}`)).toBeInTheDocument();
    expect(screen.getByTestId(`antispam-restore-unavailable-${LOG_IDS.tgDeleted}`)).toBeInTheDocument();
    expect(screen.getByTestId(`antispam-log-state-${LOG_IDS.vkFailed}`)).toHaveTextContent('Ошибка');
    expect(screen.getByTestId(`antispam-log-state-${LOG_IDS.vkRestoreFailed}`)).toHaveTextContent(
      'Ошибка восстановления',
    );
    expect(screen.getByTestId(`antispam-restore-${LOG_IDS.vkRestoreFailed}`)).toBeInTheDocument();
    expect(screen.queryByTestId(`antispam-restore-${LOG_IDS.vkFailed}`)).not.toBeInTheDocument();
    expect(screen.getByText('Стоп-слово: спам')).toBeInTheDocument();
    expect(screen.getByText('Ссылка')).toBeInTheDocument();

    await user.click(screen.getByTestId(`antispam-restore-${LOG_IDS.vkDeleted}`));
    await waitFor(() => {
      const entry = listAntispamLog({ workspaceId: PERSONAL_WORKSPACE_ID }).items.find(
        (i) => i.id === LOG_IDS.vkDeleted,
      );
      expect(entry?.state).toBe('restored');
    });
  });

  it('filters by ruleId when provided', async () => {
    renderWithProviders(
      <HistoryTable
        workspaceId={PERSONAL_WORKSPACE_ID}
        ruleId={RULE_IDS.vkActive}
        channels={channels}
        canModerate
      />,
    );

    await waitFor(() => {
      expect(screen.getByTestId(`antispam-history-item-${LOG_IDS.vkDeleted}`)).toBeInTheDocument();
    });
    expect(screen.queryByTestId(`antispam-history-item-${LOG_IDS.tgDeleted}`)).not.toBeInTheDocument();
  });
});
