import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
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

import { OrdReportsPanel } from './ord-reports-panel';
import { OrdSettingsPanel } from './ord-settings-panel';

describe('OrdReportsPanel', () => {
  beforeEach(() => {
    setAccessToken('msw.access.token');
    for (const key of Object.keys(canMap)) {
      delete canMap[key as PermissionAction];
    }
  });

  it('shows table and run/delete for owner with ord.reports.run', async () => {
    canMap['ord.view'] = true;
    canMap['ord.reports.run'] = true;

    renderWithProviders(<OrdReportsPanel workspaceId={PERSONAL_WORKSPACE_ID} />);

    await waitFor(() => {
      expect(screen.getByTestId('ord-reports-table')).toBeInTheDocument();
    });
    expect(screen.getByTestId('ord-reports-run')).toBeInTheDocument();
    expect(screen.getByTestId('ord-reports-export-csv')).toBeInTheDocument();
    expect(screen.getByText('Отправлен в ОРД')).toBeInTheDocument();
    expect(screen.getAllByTestId('ord-report-status').some((el) => el.textContent === 'Ошибка ЕРИР')).toBe(
      true,
    );
  });

  it('hides run and delete without ord.reports.run', async () => {
    canMap['ord.view'] = true;
    canMap['ord.reports.run'] = false;

    renderWithProviders(<OrdReportsPanel workspaceId={PERSONAL_WORKSPACE_ID} />);

    await waitFor(() => {
      expect(screen.getByTestId('ord-reports-table')).toBeInTheDocument();
    });
    expect(screen.queryByTestId('ord-reports-run')).not.toBeInTheDocument();
    expect(screen.queryByTestId(/ord-report-delete-/)).not.toBeInTheDocument();
    expect(screen.getByTestId('ord-reports-export-csv')).toBeInTheDocument();
  });

  it('checks ERID via MSW', async () => {
    const user = userEvent.setup();
    canMap['ord.view'] = true;
    canMap['ord.reports.run'] = true;

    renderWithProviders(<OrdReportsPanel workspaceId={PERSONAL_WORKSPACE_ID} />);

    await waitFor(() => {
      expect(screen.getByTestId('ord-reports-erid-input')).toBeInTheDocument();
    });
    await user.type(screen.getByTestId('ord-reports-erid-input'), 'EridDemoVk01');
    await user.click(screen.getByTestId('ord-reports-erid-submit'));

    await waitFor(() => {
      expect(screen.getByTestId('ord-reports-erid-result')).toBeInTheDocument();
    });
    expect(screen.getByTestId('ord-reports-erid-result')).toHaveTextContent('EridDemoVk01');
  });

  it('deletes invoice with confirmation', async () => {
    const user = userEvent.setup();
    canMap['ord.view'] = true;
    canMap['ord.reports.run'] = true;

    renderWithProviders(<OrdReportsPanel workspaceId={PERSONAL_WORKSPACE_ID} />);

    const deleteBtn = await screen.findByTestId(
      'ord-report-delete-019a0000-ordi-7000-8000-000000000301',
    );
    await user.click(deleteBtn);
    const dialog = await screen.findByTestId('ord-report-delete-dialog');
    await user.click(within(dialog).getByTestId('ord-report-delete-confirm'));

    await waitFor(() => {
      expect(
        screen.queryByTestId('ord-report-delete-019a0000-ordi-7000-8000-000000000301'),
      ).not.toBeInTheDocument();
    });
  });
});

describe('OrdSettingsPanel reports tab', () => {
  beforeEach(() => {
    setAccessToken('msw.access.token');
    for (const key of Object.keys(canMap)) {
      delete canMap[key as PermissionAction];
    }
  });

  it('shows reports tab for ord.view without run permission', async () => {
    const user = userEvent.setup();
    canMap['ord.view'] = true;
    canMap['ord.reports.run'] = false;

    renderWithProviders(<OrdSettingsPanel workspaceId={PERSONAL_WORKSPACE_ID} />);

    const tab = await screen.findByTestId('ord-tab-reports');
    await user.click(tab);
    await waitFor(() => {
      expect(screen.getByTestId('ord-reports-panel')).toBeInTheDocument();
    });
    expect(screen.queryByTestId('ord-reports-run')).not.toBeInTheDocument();
  });
});
