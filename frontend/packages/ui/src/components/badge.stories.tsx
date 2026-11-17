import type { Meta, StoryObj } from '@storybook/react';

import { Badge } from './badge';

const meta: Meta<typeof Badge> = {
  title: 'Components/Badge',
  component: Badge,
};

export default meta;
type Story = StoryObj<typeof Badge>;

export const Statuses: Story = {
  render: () => (
    <div className="flex flex-wrap gap-2">
      <Badge variant="draft">Черновик</Badge>
      <Badge variant="scheduled">Запланирован</Badge>
      <Badge variant="publishing">Публикуется</Badge>
      <Badge variant="published">Опубликован</Badge>
      <Badge variant="failed">Ошибка</Badge>
      <Badge variant="paused">Пауза</Badge>
      <Badge variant="review">На согласовании</Badge>
    </div>
  ),
};
