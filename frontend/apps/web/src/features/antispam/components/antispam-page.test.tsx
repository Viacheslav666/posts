import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { RULE_IDS, resetAntispamStore } from '@/mocks/antispam-store';
import { PERSONAL_WORKSPACE_ID } from '@/mocks/workspace-store';
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
});
