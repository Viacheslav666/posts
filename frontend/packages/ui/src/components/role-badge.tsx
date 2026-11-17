import type { HTMLAttributes } from 'react';

import { cn } from '../lib/utils';
import { Badge, type BadgeProps } from './badge';

export const WORKSPACE_ROLES = ['owner', 'admin', 'editor', 'author', 'viewer'] as const;

export type WorkspaceRole = (typeof WORKSPACE_ROLES)[number];

const ROLE_VARIANT: Record<WorkspaceRole, NonNullable<BadgeProps['variant']>> = {
  owner: 'default',
  admin: 'secondary',
  editor: 'scheduled',
  author: 'review',
  viewer: 'outline',
};

const ROLE_LABEL_RU: Record<WorkspaceRole, string> = {
  owner: 'Владелец',
  admin: 'Админ',
  editor: 'Редактор',
  author: 'Автор',
  viewer: 'Наблюдатель',
};

export type RoleBadgeProps = HTMLAttributes<HTMLDivElement> & {
  role: WorkspaceRole;
  /** Override visible label (defaults to Russian role name). */
  label?: string;
};

export function RoleBadge({ role, label, className, ...props }: RoleBadgeProps) {
  return (
    <Badge
      variant={ROLE_VARIANT[role]}
      className={cn('capitalize', className)}
      data-testid={`role-badge-${role}`}
      {...props}
    >
      {label ?? ROLE_LABEL_RU[role]}
    </Badge>
  );
}
