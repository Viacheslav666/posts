import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { patchWallet } from '@/mocks/billing-store';
import { PERSONAL_WORKSPACE_ID } from '@/mocks/workspace-store';
import { setAccessToken } from '@/shared/api-client';
import type { PermissionAction } from '@/shared/lib/permissions';
import { renderWithProviders } from '@/test/render';

const canMap: Partial<Record<PermissionAction, boolean>> = {};
const routerPush = vi.fn();

vi.mock('@/shared/hooks/use-can', () => ({
  useCan: (action: PermissionAction) => canMap[action] ?? false,
  useWorkspaceRole: () => 'owner',
}));

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: routerPush, replace: vi.fn(), prefetch: vi.fn() }),
  useParams: () => ({ workspace: PERSONAL_WORKSPACE_ID }),
  useSearchParams: () => new URLSearchParams(),
}));

import { BillingPage } from './billing-page';

describe('BillingPage (FE-10b)', () => {
  beforeEach(() => {
    setAccessToken('msw.access.token');
    routerPush.mockReset();
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

  it('renders balance, low-balance warning, top-up and history for owner', async () => {
    canMap['billing.view'] = true;
    canMap['billing.manage'] = true;

    renderWithProviders(<BillingPage workspaceId={PERSONAL_WORKSPACE_ID} />);

    expect(await screen.findByTestId('billing-page')).toBeInTheDocument();
    expect(await screen.findByTestId('billing-balance', {}, { timeout: 10_000 })).toBeInTheDocument();
    expect(await screen.findByTestId('billing-low-balance-alert')).toBeInTheDocument();
    expect(screen.getByTestId('billing-topup')).toBeInTheDocument();
    expect(screen.getByTestId('billing-autopay')).toBeInTheDocument();
    expect(await screen.findByTestId('billing-transactions')).toBeInTheDocument();
    expect(await screen.findByTestId('billing-tx-table')).toBeInTheDocument();
    expect(screen.getByTestId('billing-invoices')).toBeInTheDocument();
    expect(screen.getByTestId('billing-usage')).toBeInTheDocument();
  });

  it('shows frozen alert when wallet is frozen', async () => {
    canMap['billing.view'] = true;
    canMap['billing.manage'] = true;
    patchWallet(PERSONAL_WORKSPACE_ID, { billingStatus: 'frozen', balanceKopecks: 0 });

    renderWithProviders(<BillingPage workspaceId={PERSONAL_WORKSPACE_ID} />);

    expect(await screen.findByTestId('billing-frozen-alert', {}, { timeout: 10_000 })).toBeInTheDocument();
    expect(screen.queryByTestId('billing-low-balance-alert')).not.toBeInTheDocument();
  });

  it('creates YooKassa checkout and navigates to return URL', async () => {
    const user = userEvent.setup();
    canMap['billing.view'] = true;
    canMap['billing.manage'] = true;

    renderWithProviders(<BillingPage workspaceId={PERSONAL_WORKSPACE_ID} />);
    await screen.findByTestId('billing-topup');

    await user.click(screen.getByTestId('billing-topup-preset-2000'));
    await user.click(screen.getByTestId('billing-topup-offer'));
    await user.click(screen.getByTestId('billing-topup-submit'));

    await waitFor(() => {
      expect(routerPush).toHaveBeenCalled();
    });
    expect(String(routerPush.mock.calls[0]?.[0])).toContain('/billing/return');
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
