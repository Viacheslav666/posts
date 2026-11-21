'use client';

import { Tooltip, TooltipContent, TooltipTrigger } from '@posts/ui';
import { CircleHelp } from 'lucide-react';
import type { ReactNode } from 'react';

type FieldLabelTooltipProps = {
  label: ReactNode;
  tooltip: string;
  htmlFor?: string;
};

/** Inline field label with a Russian help tooltip (editor options). */
export function FieldLabelTooltip({ label, tooltip, htmlFor }: FieldLabelTooltipProps) {
  return (
    <span className="inline-flex items-center gap-1.5">
      {htmlFor ? (
        <label htmlFor={htmlFor} className="cursor-default">
          {label}
        </label>
      ) : (
        <span>{label}</span>
      )}
      <Tooltip>
        <TooltipTrigger asChild>
          <button
            type="button"
            className="inline-flex text-muted-foreground hover:text-foreground"
            aria-label={tooltip}
          >
            <CircleHelp className="h-3.5 w-3.5" aria-hidden />
          </button>
        </TooltipTrigger>
        <TooltipContent side="top" className="max-w-xs text-sm">
          {tooltip}
        </TooltipContent>
      </Tooltip>
    </span>
  );
}