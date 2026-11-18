'use client';

import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@posts/ui';
import type { ReactNode } from 'react';

type OptionFieldProps = {
  supported: boolean;
  unsupportedHint: string;
  children: ReactNode;
  className?: string;
};

/** Greys out unsupported network options with a tooltip (spec §3). */
export function OptionField({
  supported,
  unsupportedHint,
  children,
  className,
}: OptionFieldProps) {
  if (supported) {
    return <div className={className}>{children}</div>;
  }

  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger asChild>
          <div
            className={`opacity-50 ${className ?? ''}`}
            aria-disabled="true"
            data-unsupported="true"
          >
            <fieldset disabled className="min-w-0 border-0 p-0">
              {children}
            </fieldset>
          </div>
        </TooltipTrigger>
        <TooltipContent>{unsupportedHint}</TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
}
