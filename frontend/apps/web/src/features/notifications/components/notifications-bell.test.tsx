import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { resetNotificationStoreForTests } from '@/mocks/notification-store';
import { PERSONAL_WORKSPACE_ID } from '@/mocks/workspace-store';
import { setAccessToken } from '@/shared/api-client';
import { renderWithProviders } from '@/test/render';

vi.mock('@/features/notifications/hooks/use-notification-updates', () => ({
  useNotificationUpdates: () => 'polling',
}));

vi.mock('next/navigation', () => ({
  useParams: () => ({ workspace: PERSONAL_WORKSPACE_ID }),
  usePathname: () => `/app/${PERSONAL_WORKSPACE_ID}`,
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), prefetch: vi.fn() }),
}));

import { NotificationsBell } from './notifications-bell';

describe('NotificationsBell', () => {
  beforeEach(() => {
    setAccessToken('msw.access.token');
    resetNotificationStoreForTests();
  });

  it('shows unread count and opens panel', async () => {
    const user = userEvent.setup();
    renderWithProviders(<NotificationsBell workspaceId={PERSONAL_WORKSPACE_ID} />);

    await waitFor(() => {
      expect(screen.getByTestId('notifications-unread-count')).toBeInTheDocument();
    });

    await user.click(screen.getByTestId('notifications-bell'));
    await waitFor(() => {
      expect(screen.getByTestId('notifications-panel')).toBeInTheDocument();
    });
    expect(screen.getAllByTestId('notification-item').length).toBeGreaterThan(0);
  });
});
