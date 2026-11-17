import type { HTMLAttributes } from 'react';

import { cn } from '../lib/utils';

import { Badge } from './badge';

export type AdMarkingSlotProps = HTMLAttributes<HTMLDivElement> & {
  enabled?: boolean;
  erid?: string;
  advertiserName?: string;
};

/**
 * Extensible slot for FE-12 ORD / ad marking preview.
 * When enabled, shows the legal «Реклама» marker and optional erid.
 */
export function AdMarkingSlot({
  enabled = false,
  erid,
  advertiserName,
  className,
  ...props
}: AdMarkingSlotProps) {
  if (!enabled) {
    return null;
  }

  return (
    <div
      className={cn(
        'flex flex-wrap items-center gap-2 rounded-md border border-dashed border-border bg-muted/40 px-2 py-1.5 text-xs text-muted-foreground',
        className,
      )}
      data-testid="ad-marking-slot"
      {...props}
    >
      <Badge variant="outline" className="font-semibold uppercase tracking-wide">
        Реклама
      </Badge>
      {erid ? (
        <span className="font-mono text-[11px]" title="erid">
          erid: {erid}
        </span>
      ) : null}
      {advertiserName ? <span className="truncate">{advertiserName}</span> : null}
    </div>
  );
}
