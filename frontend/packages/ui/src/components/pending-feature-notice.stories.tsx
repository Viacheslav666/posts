import type { Meta, StoryObj } from '@storybook/react';

import { PendingFeatureNotice } from './pending-feature-notice';

const meta: Meta<typeof PendingFeatureNotice> = {
  title: 'Components/PendingFeatureNotice',
  component: PendingFeatureNotice,
};

export default meta;
type Story = StoryObj<typeof PendingFeatureNotice>;

export const FirstCommentPending: Story = {
  args: {
    title: 'Публикация первого комментария появится позже',
    body: 'Черновик и макросы уже сохраняются. Отправка в VK заработает после BE-23 (адаптеры comment).',
  },
};
