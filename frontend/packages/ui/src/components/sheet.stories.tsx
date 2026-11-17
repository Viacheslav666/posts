import type { Meta, StoryObj } from '@storybook/react';

import { Button } from './button';
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle, SheetTrigger } from './sheet';

const meta: Meta = {
  title: 'Components/Sheet',
};

export default meta;
type Story = StoryObj;

export const Default: Story = {
  render: () => (
    <Sheet>
      <SheetTrigger asChild>
        <Button variant="outline">Уведомления</Button>
      </SheetTrigger>
      <SheetContent>
        <SheetHeader>
          <SheetTitle>Уведомления</SheetTitle>
          <SheetDescription>Заглушка панели уведомлений для FE-1.</SheetDescription>
        </SheetHeader>
      </SheetContent>
    </Sheet>
  ),
};
