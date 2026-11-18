'use client';

import { useTranslations } from 'next-intl';

type AutoModeWarningProps = {
  visible: boolean;
};

export function AutoModeWarning({ visible }: AutoModeWarningProps) {
  const t = useTranslations('grabber.autoWarning');
  if (!visible) {
    return null;
  }
  return (
    <div
      role="alert"
      data-testid="grabber-auto-warning"
      className="rounded-md border border-[hsl(var(--status-scheduled))]/40 bg-[hsl(var(--status-scheduled))]/10 px-3 py-2 text-sm text-foreground"
    >
      <p className="font-medium">{t('title')}</p>
      <p className="mt-1 text-muted-foreground">{t('body')}</p>
    </div>
  );
}
