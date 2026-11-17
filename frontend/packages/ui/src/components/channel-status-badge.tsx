import type { HTMLAttributes } from 'react';

import { cn } from '../lib/utils';

import { Badge, type BadgeProps } from './badge';

export type ChannelConnectionStatus =
  | 'active'
  | 'reauth_required'
  | 'no_permissions'
  | 'disabled';

const STATUS_VARIANT: Record<ChannelConnectionStatus, NonNullable<BadgeProps['variant']>> = {
  active: 'published',
  reauth_required: 'failed',
  no_permissions: 'review',
  disabled: 'paused',
};

export type ChannelStatusBadgeProps = HTMLAttributes<HTMLDivElement> & {
  status: ChannelConnectionStatus;
  label: string;
};

export function ChannelStatusBadge({
  status,
  label,
  className,
  ...props
}: ChannelStatusBadgeProps) {
  return (
    <Badge
      variant={STATUS_VARIANT[status]}
      className={cn(className)}
      data-channel-status={status}
      {...props}
    >
      {label}
    </Badge>
  );
}
