import { screen } from '@testing-library/react';
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

import { BillingPage } from './billing-page';

describe('BillingPage', () => {
  beforeEach(() => {
    setAccessToken('msw.access.token');
    for (const key of Object.keys(canMap)) {
      delete canMap[key as PermissionAction];
    }
    window.sessionStorage.clear();
  });

  it('shows forbidden state without billing.view', async () => {
    canMap['billing.view'] = false;
    renderWithProviders(<BillingPage workspaceId={PERSONAL_WORKSPACE_ID} />);
    expect(await screen.findByText('Нет доступа к биллингу')).toBeInTheDocument();
  });

  it('renders limits scaffold for owner', async () => {
    canMap['billing.view'] = true;
    canMap['billing.manage'] = true;

    renderWithProviders(<BillingPage workspaceId={PERSONAL_WORKSPACE_ID} />);

    expect(await screen.findByTestId('billing-page')).toBeInTheDocument();
    expect(screen.getByTestId('billing-coming-soon')).toBeInTheDocument();
    expect(await screen.findByTestId('billing-current-plan', {}, { timeout: 10_000 })).toBeInTheDocument();
    expect(await screen.findByTestId('billing-usage')).toBeInTheDocument();
    expect(screen.getByTestId('billing-usage-channels')).toBeInTheDocument();
    expect(screen.getByTestId('billing-legal-form')).toBeInTheDocument();
  });

  it('validates INN/KPP on legal requisites form', async () => {
    const user = userEvent.setup();
    canMap['billing.view'] = true;
    canMap['billing.manage'] = true;

    renderWithProviders(<BillingPage workspaceId={PERSONAL_WORKSPACE_ID} />);
    await screen.findByTestId('billing-legal-form');

    await user.type(screen.getByTestId('billing-legal-inn'), '1234567890');
    await user.type(screen.getByTestId('billing-legal-kpp'), '000000000');
    await user.type(screen.getByTestId('billing-legal-ogrn'), '1027700132195');
    await user.type(screen.getByTestId('billing-legal-company'), 'ООО Тест');
    await user.type(screen.getByTestId('billing-legal-address'), 'Москва');
    await user.click(screen.getByTestId('billing-legal-submit'));

    expect(await screen.findByTestId('billing-legal-inn-error')).toBeInTheDocument();
    expect(screen.getByTestId('billing-legal-kpp-error')).toBeInTheDocument();
  });
});
