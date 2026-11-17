import { screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { PERSONAL_WORKSPACE_ID } from '@/mocks/workspace-store';
import { setAccessToken } from '@/shared/api-client';
import type { PermissionAction } from '@/shared/lib/permissions';
import { renderWithProviders } from '@/test/render';

const canMap: Partial<Record<PermissionAction, boolean>> = {};

vi.mock('@/shared/hooks/use-can', () => ({
  useCan: (action: PermissionAction) => canMap[action] ?? false,
  useWorkspaceRole: () => 'editor',
}));

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), prefetch: vi.fn() }),
  useParams: () => ({ workspace: PERSONAL_WORKSPACE_ID }),
}));

import { TeamPage } from './team-page';

describe('TeamPage role gating', () => {
  beforeEach(() => {
    setAccessToken('msw.access.token');
    for (const key of Object.keys(canMap)) {
      delete canMap[key as PermissionAction];
    }
  });

  it('shows invite form when members.invite is allowed', async () => {
    canMap['members.view'] = true;
    canMap['members.invite'] = true;
    canMap['members.update'] = true;
    canMap['members.remove'] = true;
    canMap['audit.view'] = true;

    renderWithProviders(<TeamPage workspaceId={PERSONAL_WORKSPACE_ID} />);

    await waitFor(() => {
      expect(screen.getByTestId('invite-member-form')).toBeInTheDocument();
    });
  });

  it('hides invite form for editor (no members.invite)', async () => {
    canMap['members.view'] = true;
    canMap['members.invite'] = false;
    canMap['members.update'] = false;
    canMap['members.remove'] = false;
    canMap['audit.view'] = false;

    renderWithProviders(<TeamPage workspaceId={PERSONAL_WORKSPACE_ID} />);

    await waitFor(() => {
      expect(screen.getByTestId('members-list')).toBeInTheDocument();
    });
    expect(screen.queryByTestId('invite-member-form')).not.toBeInTheDocument();
    expect(screen.queryByTestId('invite-submit')).not.toBeInTheDocument();
  });
});
