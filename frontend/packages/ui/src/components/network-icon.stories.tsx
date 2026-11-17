import type { Meta, StoryObj } from '@storybook/react';

import { NetworkIcon } from './network-icon';

const meta: Meta = {
  title: 'Components/NetworkIcon',
};

export default meta;
type Story = StoryObj;

export const All: Story = {
  render: () => (
    <div className="flex items-center gap-3">
      <NetworkIcon network="vk" />
      <NetworkIcon network="telegram" />
      <NetworkIcon network="ok" />
      <NetworkIcon network="max" />
      <NetworkIcon network="rutube" />
    </div>
  ),
};
