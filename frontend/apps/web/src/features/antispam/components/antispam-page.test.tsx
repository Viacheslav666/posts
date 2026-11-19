import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { RULE_IDS, insertAntispamRule, resetAntispamStore } from '@/mocks/antispam-store';
import { CHANNEL_IDS } from '@/mocks/channel-store';
import { DEFAULT_PROJECT_ID, PERSONAL_WORKSPACE_ID } from '@/mocks/workspace-store';
import { setAccessToken } from '@/shared/api-client';
import { renderWithProviders } from '@/test/render';

import { AntispamPage } from './antispam-page';

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), prefetch: vi.fn() }),
  useParams: () => ({ workspace: PERSONAL_WORKSPACE_ID }),
  usePathname: () => `/app/${PERSONAL_WORKSPACE_ID}/automations/antispam`,
}));

describe('AntispamPage (FE-23)', () => {
  beforeEach(() => {
    resetAntispamStore();
    setAccessToken('msw.access.token');
  });

  it('lists seeded VK and TG rules', async () => {
    renderWithProviders(<AntispamPage workspaceId={PERSONAL_WORKSPACE_ID} />);

    await waitFor(() => {
      expect(screen.getByTestId('antispam-page')).toBeInTheDocument();
    });

    const list = await screen.findByTestId('antispam-rules-list');
    expect(within(list).getByTestId(`antispam-rule-${RULE_IDS.vkActive}`)).toBeInTheDocument();
    expect(within(list).getByTestId(`antispam-rule-${RULE_IDS.tgActive}`)).toBeInTheDocument();
    expect(screen.getByTestId('antispam-create-rule')).toBeInTheDocument();
  });

  it('shows event status and can open dictionaries', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AntispamPage workspaceId={PERSONAL_WORKSPACE_ID} />);

    await waitFor(() => {
      expect(screen.getByTestId('antispam-page')).toBeInTheDocument();
    });
    await waitFor(() => {
      expect(screen.getAllByTestId('channel-event-status').length).toBeGreaterThan(0);
    });

    await user.click(screen.getByTestId('antispam-toggle-dictionaries'));
    expect(await screen.findByTestId('antispam-word-lists')).toBeInTheDocument();
  });

  it('follows nextCursor to the next rules page', async () => {
    const user = userEvent.setup();
    for (let i = 0; i < 49; i += 1) {
      insertAntispamRule({
        id: `019a0000-asrule-7000-8000-${String(200 + i).padStart(12, '0')}`,
        workspaceId: PERSONAL_WORKSPACE_ID,
        projectId: DEFAULT_PROJECT_ID,
        channelId: CHANNEL_IDS.vkNews,
        name: `Правило ${i}`,
        status: 'active',
        priority: 200 + i,
        sections: ['wall_comment'],
        version: 1,
      });
    }

    renderWithProviders(<AntispamPage workspaceId={PERSONAL_WORKSPACE_ID} />);
    await screen.findByTestId('antispam-rules-list');
    expect(screen.queryByText('Правило 48')).not.toBeInTheDocument();
    await user.click(await screen.findByTestId('antispam-rules-load-more'));
    expect(await screen.findByText('Правило 48')).toBeInTheDocument();
  });
});
