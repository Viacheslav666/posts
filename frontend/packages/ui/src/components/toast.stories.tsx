import type { Meta, StoryObj } from '@storybook/react';

import { toast } from '../hooks/use-toast';
import { Button } from './button';
import { Toaster } from './toaster';

const meta: Meta = {
  title: 'Components/Toast',
};

export default meta;
type Story = StoryObj;

export const Default: Story = {
  render: () => (
    <>
      <Button
        onClick={() =>
          toast({
            title: 'Пост сохранён',
            description: 'Черновик обновлён автоматически.',
          })
        }
      >
        Показать toast
      </Button>
      <Toaster />
    </>
  ),
};
