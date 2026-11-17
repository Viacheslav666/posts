import type { Meta, StoryObj } from '@storybook/react';

import { Button } from './button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from './dialog';

const meta: Meta = {
  title: 'Components/Dialog',
};

export default meta;
type Story = StoryObj;

export const Default: Story = {
  render: () => (
    <Dialog>
      <DialogTrigger asChild>
        <Button>Открыть диалог</Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Подтверждение</DialogTitle>
          <DialogDescription>Опубликовать пост сейчас во все выбранные каналы?</DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="secondary">Отмена</Button>
          <Button>Опубликовать</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  ),
};
