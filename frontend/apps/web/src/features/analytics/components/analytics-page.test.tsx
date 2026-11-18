import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { server } from '@/mocks/server';
import { PERSONAL_WORKSPACE_ID } from '@/mocks/workspace-store';
import { setAccessToken } from '@/shared/api-client';
import type { PermissionAction } from '@/shared/lib/permissions';
import { renderWithProviders } from '@/test/render';

const canMap: Partial<Record<PermissionAction, boolean>> = {};
const routerReplace = vi.fn();
let searchParams = new URLSearchParams('year=2026');

vi.mock('@/shared/hooks/use-can', () => ({
  useCan: (action: PermissionAction) => canMap[action] ?? false,
  useWorkspaceRole: () => 'owner',
}));

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace: routerReplace, prefetch: vi.fn() }),
  useParams: () => ({ workspace: PERSONAL_WORKSPACE_ID }),
  usePathname: () => `/app/${PERSONAL_WORKSPACE_ID}/analytics`,
  useSearchParams: () => searchParams,
}));

vi.mock('recharts', async () => {
  const actual = await vi.importActual<typeof import('recharts')>('recharts');
  return {
    ...actual,
    ResponsiveContainer: ({ children }: { children?: ReactNode }) => (
      <div data-testid="recharts-responsive">{children}</div>
    ),
  };
});

import { AnalyticsPage } from './analytics-page';

describe('AnalyticsPage (FE-19)', () => {
  beforeEach(() => {
    setAccessToken('msw.access.token');
    routerReplace.mockReset();
    searchParams = new URLSearchParams('year=2026');
    for (const key of Object.keys(canMap)) {
      delete canMap[key as PermissionAction];
    }
  });

  it('shows forbidden state without analytics.view', async () => {
    canMap['analytics.view'] = false;
    renderWithProviders(<AnalyticsPage workspaceId={PERSONAL_WORKSPACE_ID} />);
    expect(await screen.findByTestId('analytics-forbidden')).toBeInTheDocument();
    expect(screen.getByText('Нет доступа к аналитике')).toBeInTheDocument();
  });

  it('renders charts, ORD revenue and top errors for owner', async () => {
    canMap['analytics.view'] = true;
    canMap['posts.retry_publish'] = true;

    renderWithProviders(<AnalyticsPage workspaceId={PERSONAL_WORKSPACE_ID} />);

    expect(await screen.findByTestId('analytics-page')).toBeInTheDocument();
    expect(await screen.findByTestId('analytics-volume-chart', {}, { timeout: 10_000 })).toBeInTheDocument();
    expect(screen.getByTestId('analytics-networks-chart')).toBeInTheDocument();
    expect(screen.getByTestId('analytics-revenue')).toBeInTheDocument();
    expect(screen.getByTestId('analytics-revenue-total')).toBeInTheDocument();
    expect(screen.getByTestId('analytics-errors-table')).toBeInTheDocument();
    expect(screen.getAllByTestId('analytics-error-retry').length).toBeGreaterThan(0);
    expect(screen.getByTestId('analytics-volume-table')).toBeInTheDocument();
  });

  it('hides retry button without posts.retry_publish', async () => {
    canMap['analytics.view'] = true;
    canMap['posts.retry_publish'] = false;

    renderWithProviders(<AnalyticsPage workspaceId={PERSONAL_WORKSPACE_ID} />);
    await screen.findByTestId('analytics-errors-table', {}, { timeout: 10_000 });
    expect(screen.queryByTestId('analytics-error-retry')).not.toBeInTheDocument();
    expect(screen.getAllByTestId('analytics-error-open').length).toBeGreaterThan(0);
  });

  it('shows empty state for a year without publications', async () => {
    canMap['analytics.view'] = true;
    searchParams = new URLSearchParams('year=2001');

    renderWithProviders(<AnalyticsPage workspaceId={PERSONAL_WORKSPACE_ID} />);
    expect(await screen.findByTestId('analytics-empty', {}, { timeout: 10_000 })).toBeInTheDocument();
    expect(screen.getByText('Нет публикаций за год')).toBeInTheDocument();
  });

  it('shows error state with retry when API fails', async () => {
    canMap['analytics.view'] = true;
    server.use(
      http.get('/api/v1/analytics/posts', () =>
        HttpResponse.json(
          { code: 'internal_error', title: 'Error', status: 500, detail: 'boom' },
          { status: 500 },
        ),
      ),
    );

    renderWithProviders(<AnalyticsPage workspaceId={PERSONAL_WORKSPACE_ID} />);
    expect(await screen.findByTestId('analytics-error', {}, { timeout: 10_000 })).toBeInTheDocument();
    expect(screen.getByTestId('analytics-error-retry-load')).toBeInTheDocument();
  });

  it('updates year in URL when selector changes', async () => {
    const user = userEvent.setup();
    canMap['analytics.view'] = true;
    canMap['posts.retry_publish'] = true;

    renderWithProviders(<AnalyticsPage workspaceId={PERSONAL_WORKSPACE_ID} />);
    await screen.findByTestId('analytics-year-select', {}, { timeout: 10_000 });

    await user.click(screen.getByLabelText('Год'));
    const option = await screen.findByRole('option', { name: '2025' });
    await user.click(option);

    await waitFor(() => {
      expect(routerReplace).toHaveBeenCalled();
    });
    expect(String(routerReplace.mock.calls[0]?.[0])).toContain('year=2025');
  });

  it('retries publication from top errors table', async () => {
    const user = userEvent.setup();
    canMap['analytics.view'] = true;
    canMap['posts.retry_publish'] = true;

    renderWithProviders(<AnalyticsPage workspaceId={PERSONAL_WORKSPACE_ID} />);
    const table = await screen.findByTestId('analytics-errors-table', {}, { timeout: 10_000 });
    const retry = within(table).getAllByTestId('analytics-error-retry')[0]!;
    await user.click(retry);

    await waitFor(() => {
      expect(screen.getByText(/поставлена в очередь/i)).toBeInTheDocument();
    });
  });
});
