import { screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { PERSONAL_WORKSPACE_ID } from '@/mocks/workspace-store';
import { setAccessToken } from '@/shared/api-client';
import type { PermissionAction } from '@/shared/lib/permissions';
import { renderWithProviders } from '@/test/render';

const canMap: Partial<Record<PermissionAction, boolean>> = {
  'posts.view': true,
  'posts.create': true,
  'posts.retry_publish': true,
  'channels.view': true,
  'channels.manage': true,
  'automations.view': true,
};

vi.mock('@/shared/hooks/use-can', () => ({
  useCan: (action: PermissionAction) => canMap[action] ?? false,
  useWorkspaceRole: () => 'owner',
}));

vi.mock('next/navigation', () => ({
  useParams: () => ({ workspace: PERSONAL_WORKSPACE_ID }),
  usePathname: () => `/app/${PERSONAL_WORKSPACE_ID}`,
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), prefetch: vi.fn() }),
}));

import { DashboardPage } from './dashboard-page';

describe('DashboardPage', () => {
  beforeEach(() => {
    setAccessToken('msw.access.token');
  });

  it('renders dashboard sections and quick actions', async () => {
    renderWithProviders(<DashboardPage workspaceId={PERSONAL_WORKSPACE_ID} />);

    await waitFor(() => {
      expect(screen.getByTestId('dashboard-page')).toBeInTheDocument();
    });
    expect(screen.getByTestId('dashboard-quick-actions')).toBeInTheDocument();
    expect(screen.getByTestId('dashboard-upcoming')).toBeInTheDocument();
    expect(screen.getByTestId('dashboard-failed')).toBeInTheDocument();
  });
});
