import type { Meta, StoryObj } from '@storybook/react';
import { Inbox } from 'lucide-react';

import { Button } from './button';
import { EmptyState } from './empty-state';

const meta: Meta = {
  title: 'Components/EmptyState',
};

export default meta;
type Story = StoryObj;

export const Default: Story = {
  render: () => (
    <EmptyState
      icon={<Inbox aria-hidden />}
      title="Постов пока нет"
      description="Создайте первый пост или подключите канал, чтобы увидеть публикации здесь."
      action={<Button>Создать пост</Button>}
    />
  ),
};
