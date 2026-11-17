import type { HTMLAttributes } from 'react';

import { cn } from '../lib/utils';

import { Badge, type BadgeProps } from './badge';

const STATUS_VARIANT: Record<string, NonNullable<BadgeProps['variant']>> = {
  draft: 'draft',
  scheduled: 'scheduled',
  publishing: 'publishing',
  published: 'published',
  failed: 'failed',
  paused: 'paused',
  review: 'review',
  partially_published: 'publishing',
  pending_approval: 'review',
  archived: 'draft',
};

export type PostStatusBadgeProps = HTMLAttributes<HTMLDivElement> & {
  status: string;
  label: string;
};

export function PostStatusBadge({ status, label, className, ...props }: PostStatusBadgeProps) {
  const variant = STATUS_VARIANT[status] ?? 'draft';

  return (
    <Badge
      variant={variant}
      className={cn(className)}
      data-post-status={status}
      {...props}
    >
      {label}
    </Badge>
  );
}
