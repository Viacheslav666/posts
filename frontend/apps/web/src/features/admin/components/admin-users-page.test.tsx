import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import axe from 'axe-core';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { SECONDARY_USER_ID } from '@/mocks/workspace-store';
import { setAccessToken } from '@/shared/api-client';
import { renderWithProviders } from '@/test/render';

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), prefetch: vi.fn() }),
  usePathname: () => '/admin/users',
  useParams: () => ({}),
}));

import { AdminUsersPage } from './users-page';
import { AdminUserDetailPage } from './user-detail-page';

describe('Admin users (A13)', () => {
  beforeEach(() => {
    setAccessToken('msw.access.token');
  });

  it('lists users for platform admin', async () => {
    renderWithProviders(<AdminUsersPage />);
    expect(await screen.findByTestId('admin-users-page')).toBeInTheDocument();
    expect(await screen.findByTestId('admin-users-table')).toBeInTheDocument();
    expect(screen.getByText('demo@posts.app')).toBeInTheDocument();
    expect(screen.getByText('editor@posts.app')).toBeInTheDocument();
  });

  it('searches users by email', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AdminUsersPage />);
    await screen.findByTestId('admin-users-table');
    await user.clear(screen.getByTestId('admin-users-search'));
    await user.type(screen.getByTestId('admin-users-search'), 'editor@');
    await user.click(screen.getByTestId('admin-users-search-submit'));
    await waitFor(() => {
      expect(screen.getByText('editor@posts.app')).toBeInTheDocument();
    });
  });

  it('opens impersonation confirm on user detail', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AdminUserDetailPage userId={SECONDARY_USER_ID} />);
    expect(await screen.findByTestId('admin-user-detail')).toBeInTheDocument();
    await user.type(screen.getByTestId('admin-impersonate-reason'), 'support case');
    await user.click(screen.getByTestId('admin-impersonate-open'));
    expect(await screen.findByTestId('admin-impersonate-dialog')).toBeInTheDocument();
  });

  it('has no serious axe violations on users page', async () => {
    const { container } = renderWithProviders(<AdminUsersPage />);
    await screen.findByTestId('admin-users-table');
    const results = await axe.run(container, {
      rules: { 'color-contrast': { enabled: false } },
    });
    const serious = results.violations.filter(
      (item) => item.impact === 'critical' || item.impact === 'serious',
    );
    expect(serious).toEqual([]);
  });
});
