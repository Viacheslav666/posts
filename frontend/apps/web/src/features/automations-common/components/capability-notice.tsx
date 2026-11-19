'use client';

import { cn } from '@posts/ui';
import { AlertTriangle } from 'lucide-react';
import { useTranslations } from 'next-intl';

export type CapabilityNoticeVariant =
  | 'vk_user_wall'
  | 'vk_community_admin'
  | 'tg_bot_admin'
  | 'tg_bot_exclusive'
  | 'max_bot_admin'
  | 'generic';

export type CapabilityNoticeProps = {
  variant: CapabilityNoticeVariant;
  className?: string;
  title?: string;
  body?: string;
};

export function CapabilityNotice({ variant, className, title, body }: CapabilityNoticeProps) {
  const t = useTranslations('automations.common.capability');
  const resolvedTitle = title ?? t(`${variant}.title`);
  const resolvedBody = body ?? t(`${variant}.body`);

  return (
    <div
      role="status"
      data-testid="capability-notice"
      data-variant={variant}
      className={cn(
        'flex gap-3 rounded-md border border-amber-500/40 bg-amber-500/10 px-3 py-2 text-sm text-foreground',
        className,
      )}
    >
      <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-amber-700 dark:text-amber-400" aria-hidden />
      <div>
        <p className="font-medium">{resolvedTitle}</p>
        <p className="mt-1 text-muted-foreground">{resolvedBody}</p>
      </div>
    </div>
  );
}
