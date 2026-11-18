import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ITEM_IDS, resetGrabberStore, RULE_IDS } from '@/mocks/grabber-store';
import { PERSONAL_WORKSPACE_ID } from '@/mocks/workspace-store';
import { setAccessToken } from '@/shared/api-client';
import { renderWithProviders } from '@/test/render';

import { GrabberPage } from './grabber-page';

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), prefetch: vi.fn() }),
  useParams: () => ({ workspace: PERSONAL_WORKSPACE_ID }),
  usePathname: () => `/app/${PERSONAL_WORKSPACE_ID}/automations/grabber`,
}));

describe('GrabberPage (FE-14 / TC-GRAB)', () => {
  beforeEach(() => {
    resetGrabberStore();
    setAccessToken('msw.access.token');
  });

  it('defaults to moderation queue and approves item into pipeline (TC-GRAB-08/09)', async () => {
    const user = userEvent.setup();
    renderWithProviders(<GrabberPage workspaceId={PERSONAL_WORKSPACE_ID} />);

    await waitFor(() => {
      expect(screen.getByTestId('grabber-moderation-queue')).toBeInTheDocument();
    });

    const approve = await screen.findByTestId(`grabber-approve-${ITEM_IDS.pending1}`);
    await user.click(approve);

    await waitFor(() => {
      expect(screen.queryByTestId(`grabber-approve-${ITEM_IDS.pending1}`)).not.toBeInTheDocument();
    });
  });

  it('lists active/paused rules (TC-GRAB-01)', async () => {
    const user = userEvent.setup();
    renderWithProviders(<GrabberPage workspaceId={PERSONAL_WORKSPACE_ID} />);

    await waitFor(() => {
      expect(screen.getByTestId('grabber-tab-rules')).toBeInTheDocument();
    });
    await user.click(screen.getByTestId('grabber-tab-rules'));

    const list = await screen.findByTestId('grabber-rules-list');
    expect(within(list).getByTestId(`grabber-rule-${RULE_IDS.vkActive}`)).toBeInTheDocument();
    expect(within(list).getByTestId(`grabber-rule-${RULE_IDS.rssModerate}`)).toBeInTheDocument();
  });
});
