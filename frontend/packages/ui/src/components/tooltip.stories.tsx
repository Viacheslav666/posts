import type { Meta, StoryObj } from '@storybook/react';

import { Button } from './button';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from './tooltip';

const meta: Meta = {
  title: 'Components/Tooltip',
};

export default meta;
type Story = StoryObj;

export const Default: Story = {
  render: () => (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger asChild>
          <Button variant="outline">Наведите</Button>
        </TooltipTrigger>
        <TooltipContent>Создать пост (⌘N)</TooltipContent>
      </Tooltip>
    </TooltipProvider>
  ),
};
