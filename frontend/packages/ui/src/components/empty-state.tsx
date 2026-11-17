import type { HTMLAttributes, ReactNode } from 'react';

import { cn } from '../lib/utils';

export type EmptyStateProps = HTMLAttributes<HTMLDivElement> & {
  icon?: ReactNode;
  title: string;
  description?: string;
  action?: ReactNode;
  titleAs?: 'h2' | 'h3' | 'p';
};

export function EmptyState({
  className,
  icon,
  title,
  description,
  action,
  titleAs = 'h2',
  ...props
}: EmptyStateProps) {
  const TitleTag = titleAs;

  return (
    <div
      className={cn(
        'flex w-full flex-col items-center justify-center gap-3 rounded-lg border border-dashed border-border bg-muted/30 px-6 py-12 text-center',
        className,
      )}
      role="status"
      {...props}
    >
      {icon ? <div className="text-muted-foreground [&_svg]:size-10">{icon}</div> : null}
      <div className="space-y-1">
        <TitleTag className="text-base font-semibold text-foreground">{title}</TitleTag>
        {description ? <p className="max-w-sm text-sm text-muted-foreground">{description}</p> : null}
      </div>
      {action ? <div className="mt-2">{action}</div> : null}
    </div>
  );
}
