import type { Meta, StoryObj } from '@storybook/react';

import { RoleBadge, WORKSPACE_ROLES } from './role-badge';

const meta: Meta<typeof RoleBadge> = {
  title: 'Components/RoleBadge',
  component: RoleBadge,
};

export default meta;
type Story = StoryObj<typeof RoleBadge>;

export const AllRoles: Story = {
  render: () => (
    <div className="flex flex-wrap gap-2">
      {WORKSPACE_ROLES.map((role) => (
        <RoleBadge key={role} role={role} />
      ))}
    </div>
  ),
};

export const Owner: Story = {
  args: { role: 'owner' },
};

export const Editor: Story = {
  args: { role: 'editor' },
};
