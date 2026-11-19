'use client';

import { EmptyState, Skeleton, cn } from '@posts/ui';
import { useQuery } from '@tanstack/react-query';
import {
  Ban,
  GitBranch,
  MessageSquarePlus,
  ShieldOff,
  Workflow,
} from 'lucide-react';
import Link from 'next/link';
import { useTranslations } from 'next-intl';
import type { ComponentType } from 'react';

import { listIngestRules } from '@/features/grabber/api/grabber-api';
import { listProjects } from '@/features/workspace/api/workspace-api';
import { queryKeys } from '@/shared/api-client';
import { useCan } from '@/shared/hooks/use-can';

import { useAutomationFeatureFlags } from '../hooks/use-feature-flag';
import { AUTOMATIONS_DEMO_EVENT_CHANNEL_ID } from '../model/demo-channel';
import { ChannelEventStatus } from './channel-event-status';

type HubCard = {
  key: 'grabber' | 'suggest' | 'antispam' | 'crosspost';
  href: string;
  icon: ComponentType<{ className?: string; 'aria-hidden'?: boolean }>;
  activeCount?: number | null;
};

type AutomationsHubProps = {
  workspaceId: string;
};

export function AutomationsHub({ workspaceId }: AutomationsHubProps) {
  const t = useTranslations('automations');
  const canView = useCan('automations.view', workspaceId);
  const flags = useAutomationFeatureFlags();

  const projectsQuery = useQuery({
    queryKey: queryKeys.projects.list(workspaceId),
    queryFn: () => listProjects(workspaceId),
    enabled: canView,
  });
  const projectId = projectsQuery.data?.items?.[0]?.id;

  const rulesQuery = useQuery({
    queryKey: queryKeys.ingest.rules(workspaceId, projectId ?? '', {}),
    queryFn: () => listIngestRules({ workspaceId, projectId: projectId! }),
    enabled: canView && Boolean(projectId),
  });

  const grabberActive =
    rulesQuery.data?.items?.filter((rule) => rule.status === 'active').length ?? null;

  if (!canView) {
    return (
      <EmptyState
        icon={<ShieldOff className="h-6 w-6" aria-hidden />}
        title={t('common.forbiddenTitle')}
        description={t('common.forbiddenBody')}
      />
    );
  }

  const cards: HubCard[] = [
    {
      key: 'grabber',
      href: `/app/${workspaceId}/automations/grabber`,
      icon: Workflow,
      activeCount: grabberActive,
    },
  ];
  if (flags.suggest) {
    cards.push({
      key: 'suggest',
      href: `/app/${workspaceId}/automations/suggest`,
      icon: MessageSquarePlus,
      activeCount: null,
    });
  }
  if (flags.antispam) {
    cards.push({
      key: 'antispam',
      href: `/app/${workspaceId}/automations/antispam`,
      icon: Ban,
      activeCount: null,
    });
  }
  if (flags.crosspost) {
    cards.push({
      key: 'crosspost',
      href: `/app/${workspaceId}/automations/crosspost`,
      icon: GitBranch,
      activeCount: null,
    });
  }

  return (
    <div className="mx-auto flex w-full max-w-5xl flex-col gap-8" data-testid="automations-hub">
      <header className="space-y-1">
        <h1 className="text-2xl font-semibold tracking-tight">{t('hub.title')}</h1>
        <p className="max-w-2xl text-sm text-muted-foreground">{t('hub.description')}</p>
      </header>

      {projectsQuery.isLoading || rulesQuery.isLoading ? (
        <div className="grid gap-4 sm:grid-cols-2" data-testid="automations-hub-loading">
          <Skeleton className="h-36 w-full" />
          <Skeleton className="h-36 w-full" />
        </div>
      ) : (
        <ul className="grid gap-4 sm:grid-cols-2" role="list">
          {cards.map((card) => {
            const Icon = card.icon;
            return (
              <li key={card.key}>
                <Link
                  href={card.href}
                  data-testid={`automations-card-${card.key}`}
                  className={cn(
                    'flex h-full flex-col gap-3 rounded-lg border border-border bg-card/70 p-4 transition-colors',
                    'hover:border-primary/40 hover:bg-card focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
                  )}
                >
                  <div className="flex items-start gap-3">
                    <span className="rounded-md bg-primary/10 p-2 text-primary">
                      <Icon className="h-5 w-5" aria-hidden />
                    </span>
                    <div className="min-w-0 flex-1">
                      <h2 className="font-medium">{t(`${card.key}.title`)}</h2>
                      <p className="mt-1 text-sm text-muted-foreground">
                        {t(`${card.key}.cardDescription`)}
                      </p>
                    </div>
                  </div>
                  {typeof card.activeCount === 'number' ? (
                    <p className="mt-auto text-xs text-muted-foreground">
                      {t('hub.activeRules', { count: card.activeCount })}
                    </p>
                  ) : null}
                </Link>
              </li>
            );
          })}
        </ul>
      )}

      <section className="space-y-3" aria-labelledby="automations-event-status-heading">
        <div>
          <h2 id="automations-event-status-heading" className="text-lg font-medium">
            {t('common.eventStatus.demoTitle')}
          </h2>
          <p className="text-sm text-muted-foreground">{t('common.eventStatus.demoBody')}</p>
        </div>
        <ChannelEventStatus
          workspaceId={workspaceId}
          channelId={AUTOMATIONS_DEMO_EVENT_CHANNEL_ID}
        />
      </section>
    </div>
  );
}
