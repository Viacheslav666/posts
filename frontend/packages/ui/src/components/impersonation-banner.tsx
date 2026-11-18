'use client';

import type { HTMLAttributes, ReactNode } from 'react';

import { cn } from '../lib/utils';
import { Button } from './button';

export type ImpersonationBannerProps = HTMLAttributes<HTMLDivElement> & {
  /** Display name or email of the impersonated user. */
  targetLabel: string;
  reason?: string;
  onExit: () => void;
  exitLabel: string;
  title: string;
  exiting?: boolean;
  trailing?: ReactNode;
};

/**
 * Non-dismissible sticky banner shown while the access token is an impersonation session.
 * Must remain visible on every page until the admin exits.
 */
export function ImpersonationBanner({
  className,
  targetLabel,
  reason,
  onExit,
  exitLabel,
  title,
  exiting,
  trailing,
  ...props
}: ImpersonationBannerProps) {
  return (
    <div
      role="status"
      aria-live="polite"
      data-testid="impersonation-banner"
      className={cn(
        'sticky top-0 z-[60] flex flex-wrap items-center justify-between gap-3 border-b border-amber-700/40 bg-amber-500 px-4 py-3 text-amber-950 shadow-sm',
        className,
      )}
      {...props}
    >
      <div className="min-w-0 space-y-0.5">
        <p className="font-semibold leading-tight">
          {title.includes('{name}') ? title.replace('{name}', targetLabel) : title}
        </p>
        {reason ? <p className="text-sm opacity-90">{reason}</p> : null}
      </div>
      <div className="flex shrink-0 items-center gap-2">
        {trailing}
        <Button
          type="button"
          variant="secondary"
          size="sm"
          disabled={exiting}
          onClick={onExit}
          data-testid="impersonation-exit"
          className="border border-amber-900/20 bg-amber-50 text-amber-950 hover:bg-white"
        >
          {exitLabel}
        </Button>
      </div>
    </div>
  );
}
