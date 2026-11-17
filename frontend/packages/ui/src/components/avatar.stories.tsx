import type { Meta, StoryObj } from '@storybook/react';

import { Avatar, AvatarFallback, AvatarImage } from './avatar';

const meta: Meta = {
  title: 'Components/Avatar',
};

export default meta;
type Story = StoryObj;

export const Fallback: Story = {
  render: () => (
    <Avatar>
      <AvatarImage alt="User" src="" />
      <AvatarFallback>АП</AvatarFallback>
    </Avatar>
  ),
};
