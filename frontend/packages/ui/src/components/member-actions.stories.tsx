import type { Meta, StoryObj } from '@storybook/react';

import { MemberActionsBar } from './member-actions';

const meta: Meta<typeof MemberActionsBar> = {
  title: 'Components/MemberActionsBar',
  component: MemberActionsBar,
};

export default meta;
type Story = StoryObj<typeof MemberActionsBar>;

export const OwnerAdmin: Story = {
  args: {
    canInvite: true,
    canUpdate: true,
    canRemove: true,
  },
};

export const EditorViewer: Story = {
  args: {
    canInvite: false,
    canUpdate: false,
    canRemove: false,
  },
};

export const InviteOnly: Story = {
  args: {
    canInvite: true,
    canUpdate: false,
    canRemove: false,
  },
};
