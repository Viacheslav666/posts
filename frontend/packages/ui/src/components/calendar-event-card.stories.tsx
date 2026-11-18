import type { Meta, StoryObj } from '@storybook/react';

import { CalendarEventCard } from './calendar-event-card';

const meta: Meta<typeof CalendarEventCard> = {
  title: 'Components/CalendarEventCard',
  component: CalendarEventCard,
};

export default meta;
type Story = StoryObj<typeof CalendarEventCard>;

export const Scheduled: Story = {
  args: {
    time: '10:30',
    title: 'Анонс релиза',
    networks: ['vk', 'tg'],
    status: 'scheduled',
  },
};

export const Failed: Story = {
  args: {
    time: '14:00',
    title: 'Пост с ошибкой публикации',
    networks: ['ok'],
    status: 'failed',
  },
};

export const Selected: Story = {
  args: {
    time: '09:15',
    title: 'Выбранный пост',
    networks: ['max', 'rutube'],
    status: 'publishing',
    selected: true,
  },
};

export const Grid: Story = {
  render: () => (
    <div className="grid max-w-sm gap-2">
      <CalendarEventCard time="10:00" title="Черновик" status="draft" networks={['vk']} />
      <CalendarEventCard time="12:30" title="В очереди" status="scheduled" networks={['tg', 'ok']} />
      <CalendarEventCard time="18:00" title="Опубликован" status="published" networks={['vk']} />
    </div>
  ),
};
