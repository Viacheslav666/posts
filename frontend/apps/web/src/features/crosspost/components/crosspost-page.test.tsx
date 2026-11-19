import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { RULE_IDS, resetCrosspostStore } from '@/mocks/crosspost-store';
import { PERSONAL_WORKSPACE_ID } from '@/mocks/workspace-store';
import { setAccessToken } from '@/shared/api-client';
import { renderWithProviders } from '@/test/render';

import { CrosspostPage } from './crosspost-page';

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), prefetch: vi.fn() }),
  useParams: () => ({ workspace: PERSONAL_WORKSPACE_ID }),
  usePathname: () => `/app/${PERSONAL_WORKSPACE_ID}/automations/crosspost`,
}));

describe('CrosspostPage (FE-24)', () => {
  beforeEach(() => {
    resetCrosspostStore();
    setAccessToken('msw.access.token');
  });

  it('lists seeded VK → TG + Max rule', async () => {
    renderWithProviders(<CrosspostPage workspaceId={PERSONAL_WORKSPACE_ID} />);

    await waitFor(() => {
      expect(screen.getByTestId('crosspost-page')).toBeInTheDocument();
    });

    const list = await screen.findByTestId('crosspost-rules-list');
    expect(within(list).getByTestId(`crosspost-rule-${RULE_IDS.vkToTgMax}`)).toBeInTheDocument();
    expect(screen.getByTestId('crosspost-create-rule')).toBeInTheDocument();
  });

  it('shows event status block for source channels', async () => {
    renderWithProviders(<CrosspostPage workspaceId={PERSONAL_WORKSPACE_ID} />);

    await waitFor(() => {
      expect(screen.getByTestId('crosspost-page')).toBeInTheDocument();
    });
    await waitFor(() => {
      expect(screen.getAllByTestId('channel-event-status').length).toBeGreaterThan(0);
    });
  });
});
