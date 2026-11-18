'use client';

import { Button, EmptyState } from '@posts/ui';
import { PartyPopper } from 'lucide-react';
import Link from 'next/link';
import { useTranslations } from 'next-intl';

import { useOnboardingProgress } from '../hooks/use-onboarding-progress';
import { OnboardingChecklist } from './onboarding-checklist';

type OnboardingPageProps = {
  workspaceId: string;
};

export function OnboardingPage({ workspaceId }: OnboardingPageProps) {
  const t = useTranslations('onboarding');
  const { progress, isLoading } = useOnboardingProgress(workspaceId);

  if (!isLoading && progress?.allDone) {
    return (
      <div className="mx-auto max-w-xl" data-testid="onboarding-complete">
        <EmptyState
          icon={<PartyPopper className="h-6 w-6" aria-hidden />}
          title={t('completeTitle')}
          description={t('completeBody')}
          action={
            <Button asChild>
              <Link href={`/app/${workspaceId}`}>{t('goDashboard')}</Link>
            </Button>
          }
        />
      </div>
    );
  }

  return (
    <div className="mx-auto flex w-full max-w-2xl flex-col gap-6" data-testid="onboarding-page">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">{t('pageTitle')}</h1>
        <p className="mt-1 text-sm text-muted-foreground">{t('pageDescription')}</p>
      </div>
      <OnboardingChecklist workspaceId={workspaceId} variant="page" />
    </div>
  );
}
