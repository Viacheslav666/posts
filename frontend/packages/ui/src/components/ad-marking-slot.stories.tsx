import type { Meta, StoryObj } from '@storybook/react';

import { AdMarkingSlot } from './ad-marking-slot';

const meta: Meta<typeof AdMarkingSlot> = {
  title: 'Components/AdMarkingSlot',
  component: AdMarkingSlot,
};

export default meta;
type Story = StoryObj<typeof AdMarkingSlot>;

export const Disabled: Story = {
  args: { enabled: false },
};

export const Enabled: Story = {
  args: {
    enabled: true,
    erid: '2SDnje3xKzY',
    advertiserName: 'ООО «Бренд»',
  },
};

export const WithoutErid: Story = {
  args: {
    enabled: true,
    advertiserName: 'ИП Иванов',
  },
};
