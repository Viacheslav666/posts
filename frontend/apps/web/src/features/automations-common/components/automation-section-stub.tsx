'use client';

import { EmptyState } from '@posts/ui';
import { Construction } from 'lucide-react';
import Link from 'next/link';
import { useTranslations } from 'next-intl';

import { AUTOMATIONS_DEMO_EVENT_CHANNEL_ID } from '../model/demo-channel';
import { ChannelEventStatus } from './channel-event-status';

export type AutomationSectionStubProps = {
  workspaceId: string;
  section: 'suggest' | 'antispam' | 'crosspost';
};

export function AutomationSectionStub({ workspaceId, section }: AutomationSectionStubProps) {
  const t = useTranslations(`automations.${section}`);
  const common = useTranslations('automations.common');

  return (
    <div
      className="mx-auto flex w-full max-w-3xl flex-col gap-6"
      data-testid={`automations-${section}-stub`}
    >
      <header className="space-y-1">
        <p className="text-sm text-muted-foreground">
          <Link
            href={`/app/${workspaceId}/automations`}
            className="underline-offset-4 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            {common('backToHub')}
          </Link>
        </p>
        <h1 className="text-2xl font-semibold tracking-tight">{t('title')}</h1>
        <p className="max-w-2xl text-sm text-muted-foreground">{t('description')}</p>
      </header>

      <EmptyState
        icon={<Construction className="h-6 w-6" aria-hidden />}
        title={t('stubTitle')}
        description={t('stubBody')}
      />

      <div className="space-y-2">
        <h2 className="text-sm font-medium">{common('eventStatus.demoTitle')}</h2>
        <ChannelEventStatus
          workspaceId={workspaceId}
          channelId={AUTOMATIONS_DEMO_EVENT_CHANNEL_ID}
        />
      </div>
    </div>
  );
}
