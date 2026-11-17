'use client';

import { useTranslations } from 'next-intl';

import type { AutosaveStatus } from '../hooks/use-draft-autosave';

type AutosaveIndicatorProps = {
  status: AutosaveStatus;
  lastSavedAt: string | null;
};

export function AutosaveIndicator({ status, lastSavedAt }: AutosaveIndicatorProps) {
  const t = useTranslations('editor.autosave');

  const label =
    status === 'saving'
      ? t('saving')
      : status === 'saved'
        ? t('saved')
        : status === 'dirty'
          ? t('dirty')
          : status === 'error'
            ? t('error')
            : t('idle');

  return (
    <p className="text-xs text-muted-foreground" aria-live="polite">
      {label}
      {status === 'saved' && lastSavedAt
        ? ` · ${new Date(lastSavedAt).toLocaleTimeString()}`
        : null}
    </p>
  );
}
