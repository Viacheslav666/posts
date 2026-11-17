import type { Meta, StoryObj } from '@storybook/react';

import { ChannelStatusBadge, type ChannelConnectionStatus } from './channel-status-badge';

const LABELS: Record<ChannelConnectionStatus, string> = {
  active: 'Активен',
  reauth_required: 'Переподключить',
  no_permissions: 'Нет прав',
  disabled: 'Отключён',
};

const meta: Meta<typeof ChannelStatusBadge> = {
  title: 'Components/ChannelStatusBadge',
  component: ChannelStatusBadge,
};

export default meta;
type Story = StoryObj<typeof ChannelStatusBadge>;

export const All: Story = {
  render: () => (
    <div className="flex flex-wrap items-center gap-2">
      {(Object.keys(LABELS) as ChannelConnectionStatus[]).map((status) => (
        <ChannelStatusBadge key={status} status={status} label={LABELS[status]} />
      ))}
    </div>
  ),
};

export const Active: Story = {
  args: { status: 'active', label: LABELS.active },
};

export const ReauthRequired: Story = {
  args: { status: 'reauth_required', label: LABELS.reauth_required },
};

export const NoPermissions: Story = {
  args: { status: 'no_permissions', label: LABELS.no_permissions },
};

export const Disabled: Story = {
  args: { status: 'disabled', label: LABELS.disabled },
};
