import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { setAccessToken } from '@/shared/api-client';
import { renderWithProviders } from '@/test/render';

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), prefetch: vi.fn() }),
  usePathname: () => '/admin/queues',
  useParams: () => ({ queue: 'publish.dlq' }),
}));

import { AdminAnnouncementsPage } from './announcements-page';
import { AdminPromosPage } from './promos-page';
import { AdminQueueMessagesPage } from './queue-messages-page';
import { AdminQueuesPage } from './queues-page';

describe('Admin queues / promos / announcements', () => {
  beforeEach(() => {
    setAccessToken('msw.access.token');
  });

  it('lists queues and retries DLQ message with confirmation', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AdminQueuesPage />);
    expect(await screen.findByTestId('admin-queues-list')).toBeInTheDocument();
    expect(screen.getByText('publish.dlq')).toBeInTheDocument();

    renderWithProviders(<AdminQueueMessagesPage queue="publish.dlq" />);
    const retryButtons = await screen.findAllByTestId(/admin-queue-retry-/);
    await user.click(retryButtons[0]!);
    expect(await screen.findByTestId('admin-queue-retry-dialog')).toBeInTheDocument();
    await user.click(screen.getByTestId('admin-queue-retry-confirm'));
    await waitFor(() => {
      expect(screen.queryByTestId('admin-queue-retry-dialog')).not.toBeInTheDocument();
    });
  });

  it('creates a promo code', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AdminPromosPage />);
    await screen.findByTestId('admin-promos-page');
    await user.type(screen.getByTestId('admin-promo-code'), 'SPRING25');
    await user.clear(screen.getByTestId('admin-promo-value'));
    await user.type(screen.getByTestId('admin-promo-value'), '25');
    await user.click(screen.getByTestId('admin-promo-create-submit'));
    await waitFor(() => {
      expect(screen.getByText('SPRING25')).toBeInTheDocument();
    });
  });

  it('publishes an announcement', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AdminAnnouncementsPage />);
    await screen.findByTestId('admin-announcements-page');
    await user.type(screen.getByTestId('admin-announcement-title'), 'Новость');
    await user.type(screen.getByTestId('admin-announcement-body'), 'Текст объявления для всех');
    await user.click(screen.getByTestId('admin-announcement-publish'));
    await waitFor(() => {
      expect(screen.getByText('Новость')).toBeInTheDocument();
    });
  });
});
