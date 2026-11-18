import type { Meta, StoryObj } from '@storybook/react';

import { PostStatusBadge } from './post-status-badge';

const STATUSES: { status: string; label: string }[] = [
  { status: 'draft', label: 'Черновик' },
  { status: 'scheduled', label: 'Запланирован' },
  { status: 'publishing', label: 'Публикуется' },
  { status: 'published', label: 'Опубликован' },
  { status: 'failed', label: 'Ошибка' },
  { status: 'paused', label: 'Пауза' },
  { status: 'review', label: 'На проверке' },
  { status: 'partially_published', label: 'Частично' },
  { status: 'pending_approval', label: 'Ожидает' },
  { status: 'archived', label: 'Архив' },
];

const meta: Meta<typeof PostStatusBadge> = {
  title: 'Components/PostStatusBadge',
  component: PostStatusBadge,
};

export default meta;
type Story = StoryObj<typeof PostStatusBadge>;

export const All: Story = {
  render: () => (
    <div className="flex flex-wrap items-center gap-2">
      {STATUSES.map((item) => (
        <PostStatusBadge key={item.status} status={item.status} label={item.label} />
      ))}
    </div>
  ),
};

export const Scheduled: Story = {
  args: { status: 'scheduled', label: 'Запланирован' },
};

export const Failed: Story = {
  args: { status: 'failed', label: 'Ошибка' },
};
