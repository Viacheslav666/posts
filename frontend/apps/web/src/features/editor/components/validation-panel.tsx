'use client';

import type { ValidationIssue } from '@posts/domain';
import { useTranslations } from 'next-intl';

type ValidationPanelProps = {
  issues: ValidationIssue[];
  charCount: number;
};

export function ValidationPanel({ issues, charCount }: ValidationPanelProps) {
  const t = useTranslations('editor.validation');
  const errors = issues.filter((issue) => issue.severity === 'error');
  const warnings = issues.filter((issue) => issue.severity === 'warning');

  return (
    <section aria-label={t('label')} className="space-y-2 rounded-md border border-border p-3">
      <div className="flex items-center justify-between gap-2">
        <h2 className="text-sm font-semibold">{t('title')}</h2>
        <p className="text-xs text-muted-foreground">{t('chars', { count: charCount })}</p>
      </div>
      {issues.length === 0 ? (
        <p className="text-sm text-muted-foreground">{t('ok')}</p>
      ) : (
        <ul className="space-y-1.5">
          {errors.map((issue) => (
            <li
              key={`${issue.network}-${issue.code}-error`}
              className="text-sm text-[hsl(var(--status-failed))]"
              role="alert"
            >
              {issue.message}
            </li>
          ))}
          {warnings.map((issue) => (
            <li
              key={`${issue.network}-${issue.code}-warn`}
              className="text-sm text-[hsl(var(--status-scheduled))]"
            >
              {issue.message}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
