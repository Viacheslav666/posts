import type { Meta, StoryObj } from '@storybook/react';

import { PostPreview } from './post-preview';

const meta: Meta<typeof PostPreview> = {
  title: 'Preview/PostPreview',
  component: PostPreview,
  args: {
    channelName: 'Posts Demo',
    contentHtml: '<b>Привет</b> из <i>редактора</i>',
    contentText: 'Привет из редактора',
    mode: 'mobile',
  },
};

export default meta;

type Story = StoryObj<typeof PostPreview>;

export const Telegram: Story = { args: { network: 'telegram' } };
export const Max: Story = { args: { network: 'max' } };
export const Vk: Story = { args: { network: 'vk', contentHtml: undefined } };
export const Ok: Story = { args: { network: 'ok', contentHtml: undefined } };
