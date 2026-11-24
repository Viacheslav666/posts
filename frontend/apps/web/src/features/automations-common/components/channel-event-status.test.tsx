import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { CHANNEL_IDS } from '@/mocks/channel-store';
import { resetEventSubscriptionStore } from '@/mocks/event-subscription-store';
import { PERSONAL_WORKSPACE_ID } from '@/mocks/workspace-store';
import { setAccessToken } from '@/shared/api-client';
import type { PermissionAction } from '@/shared/lib/permissions';
import { renderWithProviders } from '@/test/render';

const canMap: Partial<Record<PermissionAction, boolean>> = {
  'automations.view': true,
  'automations.manage': true,
};

vi.mock('@/shared/hooks/use-can', () => ({
  useCan: (action: PermissionAction) => canMap[action] ?? false,
  useWorkspaceRole: () => 'owner',
}));

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), prefetch: vi.fn() }),
  useParams: () => ({ workspace: PERSONAL_WORKSPACE_ID }),
  usePathname: () => `/app/${PERSONAL_WORKSPACE_ID}/automations`,
}));

import { ChannelEventStatus } from './channel-event-status';

describe('ChannelEventStatus', () => {
  beforeEach(() => {
    resetEventSubscriptionStore();
    setAccessToken('msw.access.token');
    canMap['automations.view'] = true;
    canMap['automations.manage'] = true;
  });

  it('shows failed status with error and repair for manage role', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <ChannelEventStatus
        workspaceId={PERSONAL_WORKSPACE_ID}
        channelId={CHANNEL_IDS.vkNews}
      />,
    );

    await waitFor(() => {
      expect(screen.getByTestId('channel-event-status')).toHaveAttribute('data-status', 'failed');
    });
    expect(screen.getByTestId('channel-event-error')).toBeInTheDocument();
    expect(screen.getByTestId('channel-event-hint')).toHaveTextContent(/администратора сообщества/i);

    await user.click(screen.getByTestId('channel-event-repair'));

    await waitFor(() => {
      expect(screen.getByTestId('channel-event-status')).toHaveAttribute(
        'data-status',
        'connected',
      );
    });
    expect(screen.getByTestId('channel-event-status-badge')).toHaveTextContent('Подключено');
  });

  it('hides repair when user cannot manage automations', async () => {
    canMap['automations.manage'] = false;
    renderWithProviders(
      <ChannelEventStatus
        workspaceId={PERSONAL_WORKSPACE_ID}
        channelId={CHANNEL_IDS.vkNews}
      />,
    );

    await waitFor(() => {
      expect(screen.getByTestId('channel-event-status')).toBeInTheDocument();
    });
    expect(screen.queryByTestId('channel-event-repair')).not.toBeInTheDocument();
  });

  it('shows pending state for Max channel', async () => {
    renderWithProviders(
      <ChannelEventStatus
        workspaceId={PERSONAL_WORKSPACE_ID}
        channelId={CHANNEL_IDS.maxBrand}
      />,
    );

    await waitFor(() => {
      expect(screen.getByTestId('channel-event-status')).toHaveAttribute('data-status', 'pending');
    });
    expect(screen.getByTestId('channel-event-hint')).toHaveTextContent('Бот - администратор канала');
  });

  it('shows load error when subscription is missing', async () => {
    renderWithProviders(
      <ChannelEventStatus
        workspaceId={PERSONAL_WORKSPACE_ID}
        channelId="019a0000-chan-7000-8000-000000009999"
      />,
    );

    await waitFor(() => {
      expect(screen.getByTestId('channel-event-status-error')).toBeInTheDocument();
    });
  });
});
