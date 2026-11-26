import type { Notification } from '@posts/api-client';
import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { renderWithProviders } from '@/test/render';

import { NotificationItem } from './notification-item';

const workspaceId = '11111111-1111-1111-1111-111111111111';
const postId = '22222222-2222-2222-2222-222222222222';

describe('NotificationItem', () => {
  it('shows the publication failure reason and a link to the post', () => {
    renderWithProviders(
      <NotificationItem
        workspaceId={workspaceId}
        notification={
          {
            id: 'n1',
            type: 'publication.failed',
            createdAt: '2026-11-26T00:00:00Z',
            payload: {
              postId,
              errorCode: 'no_permissions',
              errorMessage:
                'VK не разрешает публикацию с этим типом подключения. Переподключите канал.',
            },
          } as unknown as Notification
        }
      />,
    );

    expect(screen.getByText('Ошибка публикации')).toBeInTheDocument();
    expect(
      screen.getByText('VK не разрешает публикацию с этим типом подключения. Переподключите канал.'),
    ).toBeInTheDocument();
    expect(screen.getByRole('link')).toHaveAttribute('href', `/app/${workspaceId}/posts/${postId}`);
  });

  it('titles post.scheduled instead of the generic fallback', () => {
    renderWithProviders(
      <NotificationItem
        workspaceId={workspaceId}
        notification={
          {
            id: 'n2',
            type: 'post.scheduled',
            readAt: '2026-11-26T00:00:00Z',
            createdAt: '2026-11-26T00:00:00Z',
            payload: { postId },
          } as unknown as Notification
        }
      />,
    );

    expect(screen.getByText('Пост запланирован')).toBeInTheDocument();
    expect(screen.queryByText('Уведомление')).not.toBeInTheDocument();
    expect(screen.getByRole('link')).toHaveAttribute('href', `/app/${workspaceId}/posts/${postId}`);
  });
});
