import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { CHANNEL_IDS } from '@/mocks/channel-store';
import { ITEM_IDS, resetCrosspostStore, RULE_IDS } from '@/mocks/crosspost-store';
import { PERSONAL_WORKSPACE_ID } from '@/mocks/workspace-store';
import { setAccessToken } from '@/shared/api-client';
import { renderWithProviders } from '@/test/render';

import { JournalTable } from './journal-table';
import { PreviewPanel } from './preview-panel';
import { RuleStatusBadge } from './rule-status-badge';

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), prefetch: vi.fn() }),
}));

describe('JournalTable (FE-24)', () => {
  beforeEach(() => {
    resetCrosspostStore();
    setAccessToken('msw.access.token');
  });

  it('renders journal rows with constructed source permalink', async () => {
    renderWithProviders(
      <JournalTable
        workspaceId={PERSONAL_WORKSPACE_ID}
        ruleId={RULE_IDS.vkToTgMax}
        sourceChannel={{
          id: CHANNEL_IDS.vkNews,
          network: 'vk',
          title: 'Posts - новости',
          externalId: '123456',
        }}
        canModerate
      />,
    );

    await waitFor(() => {
      expect(screen.getByTestId('crosspost-journal')).toBeInTheDocument();
    });
    expect(screen.getByTestId(`crosspost-journal-item-${ITEM_IDS.published1}`)).toBeInTheDocument();
    expect(screen.getByTestId(`crosspost-source-link-${ITEM_IDS.published1}`)).toHaveAttribute(
      'href',
      'https://vk.com/wall-123456_10',
    );
  });

  it('retries failed item when moderate permission present', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <JournalTable
        workspaceId={PERSONAL_WORKSPACE_ID}
        ruleId={RULE_IDS.vkToTgMax}
        canModerate
      />,
    );

    const retry = await screen.findByTestId(`crosspost-retry-${ITEM_IDS.failed1}`);
    await user.click(retry);

    await waitFor(() => {
      expect(screen.queryByTestId(`crosspost-retry-${ITEM_IDS.failed1}`)).not.toBeInTheDocument();
    });
  });

  it('hides retry without moderate permission', async () => {
    renderWithProviders(
      <JournalTable
        workspaceId={PERSONAL_WORKSPACE_ID}
        ruleId={RULE_IDS.vkToTgMax}
        canModerate={false}
      />,
    );

    await waitFor(() => {
      expect(screen.getByTestId('crosspost-journal')).toBeInTheDocument();
    });
    expect(screen.queryByTestId(`crosspost-retry-${ITEM_IDS.failed1}`)).not.toBeInTheDocument();
  });
});

describe('PreviewPanel (FE-24)', () => {
  beforeEach(() => {
    resetCrosspostStore();
    setAccessToken('msw.access.token');
  });

  it('runs preview for remote id', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <PreviewPanel
        ruleId={RULE_IDS.vkToTgMax}
        targets={[
          {
            id: CHANNEL_IDS.tgNews,
            network: 'tg',
            title: '@posts_news',
          },
          {
            id: CHANNEL_IDS.maxBrand,
            network: 'max',
            title: 'Posts Max',
          },
        ]}
      />,
    );

    await user.type(screen.getByTestId('crosspost-preview-remote'), 'wall-123456_99');
    await user.click(screen.getByTestId('crosspost-preview-submit'));

    await waitFor(() => {
      expect(screen.getByTestId('crosspost-preview-result')).toBeInTheDocument();
    });
  });
});

describe('RuleStatusBadge', () => {
  it('renders auto_paused label', () => {
    renderWithProviders(<RuleStatusBadge status="auto_paused" />);
    expect(screen.getByTestId('crosspost-rule-status')).toHaveTextContent(/Автопауза/i);
  });
});
