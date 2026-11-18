import type { HTMLAttributes } from 'react';

import { cn } from '../lib/utils';

export type PendingFeatureNoticeProps = HTMLAttributes<HTMLDivElement> & {
  title: string;
  body: string;
};

/**
 * Honest “coming later” banner for backend stubs (e.g. pending_be23).
 */
export function PendingFeatureNotice({
  title,
  body,
  className,
  ...props
}: PendingFeatureNoticeProps) {
  return (
    <div
      role="status"
      data-testid="pending-feature-notice"
      className={cn(
        'rounded-md border border-dashed border-border bg-muted/40 px-3 py-2 text-sm text-foreground',
        className,
      )}
      {...props}
    >
      <p className="font-medium">{title}</p>
      <p className="mt-1 text-muted-foreground">{body}</p>
    </div>
  );
}
