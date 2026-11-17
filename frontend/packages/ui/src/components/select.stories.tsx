import type { Meta, StoryObj } from '@storybook/react';

import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from './select';

const meta: Meta = {
  title: 'Components/Select',
};

export default meta;
type Story = StoryObj;

export const Default: Story = {
  render: () => (
    <Select defaultValue="calendar">
      <SelectTrigger className="w-56" aria-label="Раздел">
        <SelectValue placeholder="Выберите раздел" />
      </SelectTrigger>
      <SelectContent>
        <SelectItem value="calendar">Календарь</SelectItem>
        <SelectItem value="posts">Посты</SelectItem>
        <SelectItem value="channels">Каналы</SelectItem>
      </SelectContent>
    </Select>
  ),
};
