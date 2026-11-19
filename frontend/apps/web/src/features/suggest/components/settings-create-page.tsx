'use client';

import { Button, EmptyState, Skeleton } from '@posts/ui';
import { useQuery } from '@tanstack/react-query';
import { ArrowLeft, ShieldOff } from 'lucide-react';
import Link from 'next/link';
import { useTranslations } from 'next-intl';

import { listChannels } from '@/features/channels/api/channels-api';
import { listProjects } from '@/features/workspace/api/workspace-api';
import { queryKeys } from '@/shared/api-client';
import { useCan } from '@/shared/hooks/use-can';
import { getErrorMessage } from '@/shared/lib/errors';

import { SettingsForm } from './settings-form';

type SettingsCreatePageProps = {
  workspaceId: string;
};

export function SettingsCreatePage({ workspaceId }: SettingsCreatePageProps) {
  const t = useTranslations('automations.suggest');
  const canView = useCan('automations.view', workspaceId);
  const canManage = useCan('automations.manage', workspaceId);

  const projectsQuery = useQuery({
    queryKey: queryKeys.projects.list(workspaceId),
    queryFn: () => listProjects(workspaceId),
    enabled: canView,
  });
  const channelsQuery = useQuery({
    queryKey: queryKeys.channels.list(workspaceId),
    queryFn: () => listChannels({ workspaceId, limit: 100 }),
    enabled: canView,
  });

  if (!canView || !canManage) {
    return (
      <EmptyState
        icon={<ShieldOff className="h-6 w-6" aria-hidden />}
        title={t('forbiddenTitle')}
        description={t('forbiddenBody')}
      />
    );
  }

  if (projectsQuery.isError || channelsQuery.isError) {
    const error = projectsQuery.error ?? channelsQuery.error;
    return (
      <EmptyState
        role="alert"
        title={t('loadErrorTitle')}
        description={getErrorMessage(error)}
        action={
          <Button
            type="button"
            variant="outline"
            onClick={() => {
              void projectsQuery.refetch();
              void channelsQuery.refetch();
            }}
          >
            {t('retry')}
          </Button>
        }
      />
    );
  }

  if (projectsQuery.isLoading || channelsQuery.isLoading) {
    return <Skeleton className="h-64 w-full" />;
  }

  const project = projectsQuery.data?.items?.[0];
  if (!project) {
    return <EmptyState title={t('notFoundTitle')} description={t('notFoundBody')} />;
  }

  return (
    <div className="mx-auto flex w-full max-w-5xl flex-col gap-6" data-testid="suggest-settings-create">
      <div className="flex items-center gap-3">
        <Button asChild variant="ghost" size="sm">
          <Link href={`/app/${workspaceId}/automations/suggest`}>
            <ArrowLeft className="mr-1 h-4 w-4" aria-hidden />
            {t('back')}
          </Link>
        </Button>
        <h1 className="text-xl font-semibold">{t('createTitle')}</h1>
      </div>
      <SettingsForm
        workspaceId={workspaceId}
        project={project}
        channels={channelsQuery.data?.items ?? []}
        canManage={canManage}
      />
    </div>
  );
}
