import type { Meta, StoryObj } from '@storybook/react';

import { Skeleton } from './skeleton';

const meta: Meta = {
  title: 'Components/Skeleton',
};

export default meta;
type Story = StoryObj;

export const Default: Story = {
  render: () => (
    <div className="w-72 space-y-3">
      <Skeleton className="h-4 w-2/3" />
      <Skeleton className="h-4 w-full" />
      <Skeleton className="h-24 w-full" />
    </div>
  ),
};
