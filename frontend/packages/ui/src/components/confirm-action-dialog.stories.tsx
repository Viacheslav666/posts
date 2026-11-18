import type { Meta, StoryObj } from '@storybook/react';
import { fn } from '@storybook/test';
import { useState } from 'react';

import { Button } from './button';
import { ConfirmActionDialog } from './confirm-action-dialog';

const meta: Meta<typeof ConfirmActionDialog> = {
  title: 'Components/ConfirmActionDialog',
  component: ConfirmActionDialog,
};

export default meta;
type Story = StoryObj<typeof ConfirmActionDialog>;

function Demo() {
  const [open, setOpen] = useState(true);
  return (
    <>
      <Button onClick={() => setOpen(true)}>Открыть</Button>
      <ConfirmActionDialog
        open={open}
        onOpenChange={setOpen}
        title="Повторить сообщение из DLQ?"
        description="Сообщение будет возвращено в основную очередь. Действие аудируется."
        confirmLabel="Повторить"
        cancelLabel="Отмена"
        onConfirm={() => setOpen(false)}
      />
    </>
  );
}

export const Default: Story = {
  render: () => <Demo />,
};

export const Static: Story = {
  args: {
    open: true,
    onOpenChange: fn(),
    title: 'Отключить feature flag?',
    description: 'Флаг станет недоступен для всех workspace.',
    confirmLabel: 'Отключить',
    cancelLabel: 'Отмена',
    onConfirm: fn(),
  },
};
