import type { HTMLAttributes } from 'react';

import { cn } from '../lib/utils';

export type MacroHintsProps = HTMLAttributes<HTMLUListElement> & {
  macros: readonly string[];
  label?: string;
};

/**
 * Inline list of supported text macros for grabber prepend/append / first comment.
 */
export function MacroHints({ macros, label, className, ...props }: MacroHintsProps) {
  return (
    <div className={cn('space-y-1', className)}>
      {label ? <p className="text-xs font-medium text-muted-foreground">{label}</p> : null}
      <ul
        className="flex flex-wrap gap-1.5"
        data-testid="macro-hints"
        {...props}
      >
        {macros.map((macro) => (
          <li key={macro}>
            <code className="rounded bg-muted px-1.5 py-0.5 font-mono text-[11px] text-foreground">
              {macro}
            </code>
          </li>
        ))}
      </ul>
    </div>
  );
}
