import type { Meta, StoryObj } from '@storybook/react';
import { fn } from '@storybook/test';

import { ImpersonationBanner } from './impersonation-banner';

const meta: Meta<typeof ImpersonationBanner> = {
  title: 'Components/ImpersonationBanner',
  component: ImpersonationBanner,
  args: {
    title: 'Вы вошли как {name}',
    targetLabel: 'user@example.com',
    reason: 'Поддержка: разбор инцидента PUB-42',
    exitLabel: 'Выйти из имперсонации',
    onExit: fn(),
  },
};

export default meta;
type Story = StoryObj<typeof ImpersonationBanner>;

export const Default: Story = {};

export const WithoutReason: Story = {
  args: {
    reason: undefined,
  },
};
