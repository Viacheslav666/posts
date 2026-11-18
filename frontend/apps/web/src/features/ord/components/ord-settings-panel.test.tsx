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

import { OrdSettingsPanel } from './ord-settings-panel';

describe('OrdSettingsPanel', () => {
  beforeEach(() => {
    setAccessToken('msw.access.token');
    for (const key of Object.keys(canMap)) {
      delete canMap[key as PermissionAction];
    }
  });

  it('renders heading and connection form for owner-like permissions', async () => {
    canMap['ord.view'] = true;
    canMap['ord.connection.manage'] = true;
    canMap['ord.contractors.manage'] = true;
    canMap['ord.reports.run'] = true;

    renderWithProviders(<OrdSettingsPanel workspaceId={PERSONAL_WORKSPACE_ID} />);

    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Реклама / ОРД');

    await waitFor(() => {
      expect(screen.getByTestId('ord-connection-form')).toBeInTheDocument();
    });
    expect(screen.getByTestId('ord-settings-page')).toBeInTheDocument();
  });
});