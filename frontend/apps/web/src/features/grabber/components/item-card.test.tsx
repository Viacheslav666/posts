import type { GrabberItem } from '@posts/api-client';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { renderWithProviders } from '@/test/render';

import { ItemCard } from './item-card';

const failedItem: GrabberItem = {
  id: '019a0000-item-7000-8000-000000000001',
  status: 'publish_failed',
  skipReason: 'video_reupload_failed:video_too_large',
  contentText: 'Пост с большим видео',
  sourceNetwork: 'vk',
};

function renderCard(item: GrabberItem, onApprove = vi.fn()) {
  renderWithProviders(
    <ItemCard
      item={item}
      selected={false}
      canModerate
      onToggle={vi.fn()}
      onApprove={onApprove}
      onReject={vi.fn()}
      onEdit={vi.fn()}
    />,
  );
  return onApprove;
}

describe('ItemCard media failures', () => {
  it('explains a failed video re-upload and offers a retry', async () => {
    const user = userEvent.setup();
    const onApprove = renderCard(failedItem);

    expect(screen.getByText('Ошибка публикации')).toBeInTheDocument();
    expect(screen.getByTestId(`grabber-item-failure-${failedItem.id}`)).toHaveTextContent(
      /больше 50 МБ/,
    );
    await user.click(screen.getByTestId(`grabber-retry-${failedItem.id}`));
    expect(onApprove).toHaveBeenCalledWith(failedItem.id);
  });

  it('falls back to a generic reason for unknown failure codes', () => {
    renderCard({ ...failedItem, skipReason: 'video_reupload_failed:upload_failed' });
    expect(screen.getByTestId(`grabber-item-failure-${failedItem.id}`)).toHaveTextContent(
      /ошибки обработки вложений/,
    );
  });

  it('shows no failure block for pending items', () => {
    renderCard({ ...failedItem, status: 'pending_moderation', skipReason: undefined });
    expect(screen.queryByTestId(`grabber-item-failure-${failedItem.id}`)).not.toBeInTheDocument();
    expect(screen.queryByTestId(`grabber-retry-${failedItem.id}`)).not.toBeInTheDocument();
  });
});
