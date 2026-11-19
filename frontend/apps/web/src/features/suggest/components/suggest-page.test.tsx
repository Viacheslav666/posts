import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { CHANNEL_IDS } from '@/mocks/channel-store';
import { ITEM_IDS, resetSuggestStore, SETTINGS_IDS } from '@/mocks/suggest-store';
import { PERSONAL_WORKSPACE_ID } from '@/mocks/workspace-store';
import { setAccessToken } from '@/shared/api-client';
import type { PermissionAction } from '@/shared/lib/permissions';
import { renderWithProviders } from '@/test/render';

import { SuggestPage } from './suggest-page';

const canMap: Partial<Record<PermissionAction, boolean>> = {
  'automations.view': true,
  'automations.manage': true,
  'automations.moderate': true,
};

vi.mock('@/shared/hooks/use-can', () => ({
  useCan: (action: PermissionAction) => canMap[action] ?? false,
}));

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), prefetch: vi.fn() }),
  useParams: () => ({ workspace: PERSONAL_WORKSPACE_ID }),
  usePathname: () => `/app/${PERSONAL_WORKSPACE_ID}/automations/suggest`,
}));

describe('SuggestPage (FE-22)', () => {
  beforeEach(() => {
    resetSuggestStore();
    setAccessToken('msw.access.token');
    canMap['automations.view'] = true;
    canMap['automations.manage'] = true;
    canMap['automations.moderate'] = true;
  });

  it('lists seeded settings with status and pending count', async () => {
    renderWithProviders(<SuggestPage workspaceId={PERSONAL_WORKSPACE_ID} />);

    await waitFor(() => {
      expect(screen.getByTestId('suggest-page')).toBeInTheDocument();
    });

    const card = await screen.findByTestId(`suggest-settings-${SETTINGS_IDS.vkNews}`);
    expect(within(card).getAllByText(/Активна/i).length).toBeGreaterThan(0);
    await waitFor(() => {
      expect(screen.getByTestId(`suggest-pending-count-${SETTINGS_IDS.vkNews}`)).toBeInTheDocument();
    });
  });

  it('shows connect channel control for managers', async () => {
    renderWithProviders(<SuggestPage workspaceId={PERSONAL_WORKSPACE_ID} />);
    await waitFor(() => {
      expect(screen.getByTestId('suggest-connect-channel')).toBeInTheDocument();
    });
  });

  it('hides manage actions without automations.manage', async () => {
    canMap['automations.manage'] = false;
    renderWithProviders(<SuggestPage workspaceId={PERSONAL_WORKSPACE_ID} />);

    await waitFor(() => {
      expect(screen.getByTestId('suggest-page')).toBeInTheDocument();
    });
    expect(screen.queryByTestId('suggest-connect-channel')).not.toBeInTheDocument();
  });

  it('shows forbidden state without automations.view', async () => {
    canMap['automations.view'] = false;
    renderWithProviders(<SuggestPage workspaceId={PERSONAL_WORKSPACE_ID} />);
    expect(await screen.findByText(/Недостаточно прав/i)).toBeInTheDocument();
  });

  it('opens channel picker when connecting', async () => {
    const user = userEvent.setup();
    renderWithProviders(<SuggestPage workspaceId={PERSONAL_WORKSPACE_ID} />);
    await user.click(await screen.findByTestId('suggest-connect-channel'));
    expect(await screen.findByTestId('suggest-connect-picker')).toBeInTheDocument();
    expect(CHANNEL_IDS.vkShop).toBeTruthy();
  });
});

describe('Suggest seed ids', () => {
  it('seed pending item ids are stable for e2e', () => {
    expect(ITEM_IDS.pending1).toMatch(/sitem/);
  });
});
