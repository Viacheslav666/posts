import type { HTMLAttributes, ReactNode } from 'react';

import { cn } from '../lib/utils';

export type SettingsSectionProps = HTMLAttributes<HTMLFieldSetElement> & {
  title: string;
  description?: string;
  children: ReactNode;
};

/**
 * Accessible settings group: fieldset + legend for form parity sections.
 */
export function SettingsSection({
  title,
  description,
  children,
  className,
  ...props
}: SettingsSectionProps) {
  return (
    <fieldset
      className={cn('space-y-3 rounded-md border border-border p-4', className)}
      {...props}
    >
      <legend className="px-1 text-sm font-semibold text-foreground">{title}</legend>
      {description ? <p className="text-xs text-muted-foreground">{description}</p> : null}
      <div className="space-y-3">{children}</div>
    </fieldset>
  );
}
