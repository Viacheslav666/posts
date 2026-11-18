import type { Meta, StoryObj } from '@storybook/react';

import { Progress } from './progress';

const meta: Meta<typeof Progress> = {
  title: 'Components/Progress',
  component: Progress,
};

export default meta;
type Story = StoryObj<typeof Progress>;

export const Levels: Story = {
  render: () => (
    <div className="flex w-72 flex-col gap-4">
      <div className="space-y-1">
        <p className="text-sm text-muted-foreground">30%</p>
        <Progress value={30} aria-label="Usage 30 percent" />
      </div>
      <div className="space-y-1">
        <p className="text-sm text-muted-foreground">85%</p>
        <Progress value={85} aria-label="Usage 85 percent" />
      </div>
      <div className="space-y-1">
        <p className="text-sm text-muted-foreground">100%</p>
        <Progress value={100} aria-label="Usage 100 percent" />
      </div>
    </div>
  ),
};
