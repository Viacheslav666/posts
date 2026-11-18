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

const listPostsMock = vi.fn();

vi.mock('@/features/posts/api/posts-api', async () => {
  const actual = await vi.importActual<typeof import('@/features/posts/api/posts-api')>(
    '@/features/posts/api/posts-api',
  );
  return {
    ...actual,
    listPosts: (...args: unknown[]) => listPostsMock(...args),
  };
});

import { DashboardPage } from './dashboard-page';

describe('DashboardPage', () => {
  beforeEach(() => {
    setAccessToken('msw.access.token');
    listPostsMock.mockReset();
    listPostsMock.mockImplementation(async (params: { contentKind?: string; status?: string }) => {
      if (params.contentKind === 'story') {
        return {
          items: [
            {
              id: 'story-1',
              title: 'Вечерняя история VK',
              status: 'scheduled',
              contentKind: 'story',
              scheduledAt: new Date(Date.now() + 3_600_000).toISOString(),
            },
          ],
        };
      }
      if (params.status === 'failed') {
        return {
          items: [
            {
              id: 'failed-1',
              title: 'Акция недели',
              status: 'failed',
              contentKind: 'post',
            },
          ],
        };
      }
      return {
        items: [
          {
            id: 'post-1',
            title: 'Утренний дайджест',
            status: 'scheduled',
            contentKind: 'post',
            scheduledAt: new Date(Date.now() + 6 * 3_600_000).toISOString(),
          },
          // Should be filtered out if API ignores contentKind
          {
            id: 'story-leak',
            title: 'Утечка истории',
            status: 'scheduled',
            contentKind: 'story',
            scheduledAt: new Date(Date.now() + 2 * 3_600_000).toISOString(),
          },
        ],
      };
    });
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

  it('loads upcoming posts without perpetual skeletons and without freezing time', async () => {
    renderWithProviders(<DashboardPage workspaceId={PERSONAL_WORKSPACE_ID} />);

    await waitFor(() => {
      expect(screen.getByText('Утренний дайджест')).toBeInTheDocument();
    });
    expect(screen.queryByTestId('dashboard-upcoming-skeleton')).not.toBeInTheDocument();
    expect(screen.getAllByText('Запланирован').length).toBeGreaterThan(0);
    expect(screen.queryByText('scheduled')).not.toBeInTheDocument();
  });

  it('does not thrash listPosts when the page re-renders', async () => {
    const { rerender } = renderWithProviders(
      <DashboardPage workspaceId={PERSONAL_WORKSPACE_ID} />,
    );

    await waitFor(() => {
      expect(screen.getByText('Утренний дайджест')).toBeInTheDocument();
    });

    const callsAfterLoad = listPostsMock.mock.calls.length;
    expect(callsAfterLoad).toBeGreaterThan(0);

    rerender(<DashboardPage workspaceId={PERSONAL_WORKSPACE_ID} />);
    rerender(<DashboardPage workspaceId={PERSONAL_WORKSPACE_ID} />);

    await new Promise((r) => setTimeout(r, 50));
    expect(listPostsMock.mock.calls.length).toBe(callsAfterLoad);
  });

  it('requests upcoming with contentKind=post and hides story duplicates', async () => {
    renderWithProviders(<DashboardPage workspaceId={PERSONAL_WORKSPACE_ID} />);

    await waitFor(() => {
      expect(screen.getByText('Утренний дайджест')).toBeInTheDocument();
    });

    const upcomingCalls = listPostsMock.mock.calls.filter(
      (call) => (call[0] as { contentKind?: string }).contentKind === 'post',
    );
    expect(upcomingCalls.length).toBeGreaterThan(0);
    expect(screen.queryByText('Утечка истории')).not.toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText('Вечерняя история VK')).toBeInTheDocument();
    });
  });
});
