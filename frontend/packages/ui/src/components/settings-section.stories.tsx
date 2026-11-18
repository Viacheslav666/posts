import type { Meta, StoryObj } from '@storybook/react';

import { SettingsSection } from './settings-section';

const meta: Meta<typeof SettingsSection> = {
  title: 'Components/SettingsSection',
  component: SettingsSection,
};

export default meta;
type Story = StoryObj<typeof SettingsSection>;

export const Default: Story = {
  args: {
    title: 'Не публиковать, если',
    description: 'Фильтры пропуска кандидатов до очереди модерации.',
    children: (
      <label className="inline-flex items-center gap-2 text-sm">
        <input type="checkbox" defaultChecked />
        Реклама
      </label>
    ),
  },
};

export const WithoutDescription: Story = {
  args: {
    title: 'Лимиты',
    children: <p className="text-sm text-muted-foreground">До 50 постов в сутки</p>,
  },
};
