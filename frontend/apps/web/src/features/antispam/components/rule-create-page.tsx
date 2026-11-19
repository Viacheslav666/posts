'use client';

import { EmptyState, Skeleton } from '@posts/ui';
import { useQuery } from '@tanstack/react-query';
import { Radio, ShieldOff } from 'lucide-react';
import { useTranslations } from 'next-intl';

import { listChannels } from '@/features/channels/api/channels-api';
import { listProjects } from '@/features/workspace/api/workspace-api';
import { queryKeys } from '@/shared/api-client';
import { useCan } from '@/shared/hooks/use-can';

import { ANTISPAM_NETWORKS } from '../model/types';
import { RuleForm } from './rule-form';

type RuleCreatePageProps = {
  workspaceId: string;
};

export function RuleCreatePage({ workspaceId }: RuleCreatePageProps) {
  const t = useTranslations('automations.antispam');
  const canManage = useCan('automations.manage', workspaceId);

  const projectsQuery = useQuery({
    queryKey: queryKeys.projects.list(workspaceId),
    queryFn: () => listProjects(workspaceId),
    enabled: canManage,
  });
  const channelsQuery = useQuery({
    queryKey: queryKeys.channels.list(workspaceId),
    queryFn: () => listChannels({ workspaceId, limit: 100 }),
    enabled: canManage,
  });

  if (!canManage) {
    return (
      <EmptyState
        icon={<ShieldOff className="h-6 w-6" aria-hidden />}
        title={t('forbiddenTitle')}
        description={t('forbiddenManageBody')}
      />
    );
  }

  if (projectsQuery.isLoading || channelsQuery.isLoading) {
    return <Skeleton className="h-64 w-full" />;
  }

  const project = projectsQuery.data?.items?.[0];
  if (!project) {
    return <EmptyState title={t('noProjectTitle')} description={t('noProjectBody')} />;
  }

  const channels = channelsQuery.data?.items ?? [];
  const antispamChannels = channels.filter(
    (channel) =>
      channel.status === 'active' &&
      channel.network &&
      ANTISPAM_NETWORKS.includes(channel.network as (typeof ANTISPAM_NETWORKS)[number]),
  );

  if (antispamChannels.length === 0) {
    return (
      <EmptyState
        icon={<Radio className="h-6 w-6" aria-hidden />}
        title={t('emptyChannelsTitle')}
        description={t('emptyChannelsBody')}
      />
    );
  }

  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-6" data-testid="antispam-rule-create">
      <header>
        <h1 className="text-2xl font-semibold">{t('createTitle')}</h1>
        <p className="mt-1 text-sm text-muted-foreground">{t('createDescription')}</p>
      </header>
      <RuleForm workspaceId={workspaceId} project={project} channels={channels} />
    </div>
  );
}
