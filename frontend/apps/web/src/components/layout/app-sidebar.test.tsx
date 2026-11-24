import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { renderWithProviders } from '@/test/render';

import { AppSidebar } from './app-sidebar';

const granted = new Set<string>();

vi.mock('next/navigation', () => ({
  usePathname: () => '/app/ws/posts',
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), prefetch: vi.fn() }),
  useParams: () => ({ workspace: 'ws' }),
}));

vi.mock('@/shared/hooks/use-can', () => ({
  useCan: (action: string) => granted.has(action),
}));

vi.mock('@/shared/hooks/use-me', () => ({
  useMe: () => ({
    data: {
      name: 'Никита Галкин',
      email: 'nikita@posts.app',
      loginMethod: 'vk',
      avatarUrl: 'https://sun.vk.example/a.jpg',
      memberships: [
        { workspaceId: 'ws', workspaceName: 'Личное', role: 'owner' },
        { workspaceId: 'ws2', workspaceName: 'Второе', role: 'admin' },
      ],
      features: {
        'automation.suggest': true,
        'automation.antispam': true,
        'automation.crosspost': true,
      },
    },
    isLoading: false,
  }),
}));

vi.mock('./use-nav-badges', () => ({
  useNavBadges: () => ({ suggest: 4 }),
}));

const ALL = [
  'workspace.view',
  'workspace.update',
  'members.view',
  'members.invite',
  'members.update',
  'projects.manage',
  'projects.view',
  'billing.view',
  'channels.view',
  'channels.manage',
  'media.view',
  'media.manage',
  'posts.view',
  'posts.create',
  'posts.schedule',
  'calendar.view',
  'analytics.view',
  'ord.view',
  'ord.reports.run',
  'automations.view',
  'automations.manage',
  'notifications.manage_self',
];

describe('AppSidebar', () => {
  beforeEach(() => {
    granted.clear();
    for (const action of ALL) {
      granted.add(action);
    }
    window.localStorage.clear();
  });

  it('opens one dropdown at a time and closes it on Escape', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AppSidebar workspaceId="ws" />);

    await user.click(screen.getByTestId('nav-chevron-posts'));
    expect(screen.getByRole('menu', { name: 'Посты' })).toBeInTheDocument();

    await user.click(screen.getByTestId('nav-chevron-stories'));
    expect(screen.queryByRole('menu', { name: 'Посты' })).not.toBeInTheDocument();
    expect(screen.getByRole('menu', { name: 'Истории' })).toBeInTheDocument();

    await user.keyboard('{Escape}');
    expect(screen.queryByRole('menu', { name: 'Истории' })).not.toBeInTheDocument();
    expect(screen.getByTestId('nav-badge-suggest')).toHaveTextContent('4');
  });

  it('filters items and subitems the role cannot access', async () => {
    granted.delete('billing.view');
    granted.delete('posts.create');
    granted.delete('posts.schedule');
    const user = userEvent.setup();
    renderWithProviders(<AppSidebar workspaceId="ws" />);

    expect(screen.queryByTestId('nav-item-billing')).not.toBeInTheDocument();
    await user.click(screen.getByTestId('nav-chevron-create-post'));
    expect(screen.queryByRole('menuitem', { name: 'Пост сейчас' })).not.toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: 'Черновики' })).toBeInTheDocument();
  });

  it('shows the account trigger with a name and login method', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AppSidebar workspaceId="ws" />);
    const trigger = screen.getByTestId('user-account-menu-trigger');
    expect(trigger).toHaveAttribute('aria-label', 'Никита Галкин · VK');
    await user.click(trigger);
    expect(screen.getByRole('menuitem', { name: 'Сменить пространство' })).toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: 'Выйти' })).toBeInTheDocument();
  });
});
