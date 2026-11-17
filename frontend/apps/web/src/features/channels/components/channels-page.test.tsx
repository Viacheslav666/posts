import { screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { PERSONAL_WORKSPACE_ID } from '@/mocks/workspace-store';
import { setAccessToken } from '@/shared/api-client';
import type { PermissionAction } from '@/shared/lib/permissions';
import { renderWithProviders } from '@/test/render';

const canMap: Partial<Record<PermissionAction, boolean>> = {};

vi.mock('@/shared/hooks/use-can', () => ({
  useCan: (action: PermissionAction) => canMap[action] ?? false,
  useWorkspaceRole: () => 'owner',
}));

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), prefetch: vi.fn() }),
  useParams: () => ({ workspace: PERSONAL_WORKSPACE_ID }),
}));

import { ChannelsPage } from './channels-page';

describe('ChannelsPage', () => {
  beforeEach(() => {
    setAccessToken('msw.access.token');
    for (const key of Object.keys(canMap)) {
      delete canMap[key as PermissionAction];
    }
  });

  it('renders network grids and status badges for owner', async () => {
    canMap['channels.view'] = true;
    canMap['channels.manage'] = true;
    canMap['channel_sets.view'] = true;
    canMap['channel_sets.manage'] = true;

    renderWithProviders(<ChannelsPage workspaceId={PERSONAL_WORKSPACE_ID} />);

    await waitFor(() => {
      expect(screen.getByTestId('channels-page')).toBeInTheDocument();
    });

    expect(screen.getByRole('heading', { name: 'Каналы' })).toBeInTheDocument();
    expect(screen.getByTestId('plan-limit-banner')).toBeInTheDocument();
    expect(screen.getAllByText('Активен').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Переподключить').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Нет прав').length).toBeGreaterThan(0);
  });

  it('hides connect controls without channels.manage', async () => {
    canMap['channels.view'] = true;
    canMap['channels.manage'] = false;
    canMap['channel_sets.view'] = true;
    canMap['channel_sets.manage'] = false;

    renderWithProviders(<ChannelsPage workspaceId={PERSONAL_WORKSPACE_ID} />);

    await waitFor(() => {
      expect(screen.getByTestId('channels-page')).toBeInTheDocument();
    });

    expect(screen.queryByTestId('channels-connect')).not.toBeInTheDocument();
    expect(screen.queryByTestId('connect-network-tg')).not.toBeInTheDocument();
  });

  it('shows forbidden empty state without channels.view', async () => {
    canMap['channels.view'] = false;

    renderWithProviders(<ChannelsPage workspaceId={PERSONAL_WORKSPACE_ID} />);

    expect(await screen.findByText('Нет доступа')).toBeInTheDocument();
  });
});
