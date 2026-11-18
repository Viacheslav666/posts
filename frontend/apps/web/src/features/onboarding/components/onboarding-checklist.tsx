'use client';

import { Button, Skeleton } from '@posts/ui';
import { CheckCircle2, Circle } from 'lucide-react';
import Link from 'next/link';
import { useTranslations } from 'next-intl';

import { useOnboardingProgress } from '../hooks/use-onboarding-progress';
import type { OnboardingStepId } from '../model/onboarding';

type OnboardingChecklistProps = {
  workspaceId: string;
  /** Compact strip for dashboard; full card for /onboarding. */
  variant?: 'dashboard' | 'page';
};

function stepHref(workspaceId: string, id: OnboardingStepId): string {
  switch (id) {
    case 'workspace':
      return `/app/${workspaceId}/settings/workspace`;
    case 'channel':
      return `/app/${workspaceId}/channels/connect`;
    case 'post':
      return `/app/${workspaceId}/posts/new`;
    default:
      return `/app/${workspaceId}`;
  }
}

export function OnboardingChecklist({
  workspaceId,
  variant = 'dashboard',
}: OnboardingChecklistProps) {
  const t = useTranslations('onboarding');
  const { progress, isLoading, dismiss } = useOnboardingProgress(workspaceId);

  if (isLoading) {
    return <Skeleton className="h-28 w-full" data-testid="onboarding-loading" />;
  }

  if (!progress || progress.dismissed || progress.allDone) {
    return null;
  }

  return (
    <section
      className={
        variant === 'page'
          ? 'space-y-4 rounded-md border border-border p-6'
          : 'space-y-3 rounded-md border border-border bg-card/40 p-4'
      }
      data-testid="onboarding-checklist"
      aria-label={t('title')}
    >
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <h2 className="text-lg font-semibold text-foreground">{t('title')}</h2>
          <p className="text-sm text-muted-foreground">
            {t('progress', { done: progress.completedCount, total: progress.total })}
          </p>
        </div>
        <Button type="button" variant="ghost" size="sm" onClick={dismiss} data-testid="onboarding-skip">
          {t('skip')}
        </Button>
      </div>

      <ol className="space-y-2">
        {progress.steps.map((step) => (
          <li key={step.id} className="flex items-center gap-3">
            {step.done ? (
              <CheckCircle2 className="h-5 w-5 shrink-0 text-primary" aria-hidden />
            ) : (
              <Circle className="h-5 w-5 shrink-0 text-muted-foreground" aria-hidden />
            )}
            <div className="min-w-0 flex-1">
              <p className="text-sm font-medium text-foreground">{t(`steps.${step.id}.title`)}</p>
              <p className="text-xs text-muted-foreground">{t(`steps.${step.id}.body`)}</p>
            </div>
            {!step.done ? (
              <Button asChild size="sm" variant="secondary">
                <Link href={stepHref(workspaceId, step.id)}>{t(`steps.${step.id}.cta`)}</Link>
              </Button>
            ) : null}
          </li>
        ))}
      </ol>

      <div
        className="h-2 overflow-hidden rounded-full bg-muted"
        role="progressbar"
        aria-valuenow={progress.completedCount}
        aria-valuemin={0}
        aria-valuemax={progress.total}
        aria-label={t('progress', { done: progress.completedCount, total: progress.total })}
      >
        <div
          className="h-full bg-primary transition-all"
          style={{ width: `${(progress.completedCount / progress.total) * 100}%` }}
        />
      </div>
    </section>
  );
}
