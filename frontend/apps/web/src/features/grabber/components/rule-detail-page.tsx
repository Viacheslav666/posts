'use client';

import {
  Button,
  EmptyState,
  Skeleton,
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
  toast,
} from '@posts/ui';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, ShieldOff } from 'lucide-react';
import Link from 'next/link';
import { useTranslations } from 'next-intl';

import { listChannels } from '@/features/channels/api/channels-api';
import { listProjects } from '@/features/workspace/api/workspace-api';
import { queryKeys } from '@/shared/api-client';
import { useCan } from '@/shared/hooks/use-can';
import { getErrorMessage } from '@/shared/lib/errors';

import {
  activateIngestRule,
  getIngestRule,
  listIngestItems,
  pauseIngestRule,
  resumeIngestRule,
  startIngestRun,
} from '../api/grabber-api';
import { DeleteGrabberButton } from './delete-grabber-button';
import { HistoryExportButtons } from './history-export-buttons';
import { ModerationQueue } from './moderation-queue';
import { RuleForm } from './rule-form';
import { RuleStatusBadge } from './rule-status-badge';
import { RunsHistory } from './runs-history';

type RuleDetailPageProps = {
  workspaceId: string;
  ruleId: string;
};

export function RuleDetailPage({ workspaceId, ruleId }: RuleDetailPageProps) {
  const t = useTranslations('grabber');
  const canView = useCan('automations.view', workspaceId);
  const canManage = useCan('automations.manage', workspaceId);
  const canModerate = useCan('automations.moderate', workspaceId);
  const queryClient = useQueryClient();

  const ruleQuery = useQuery({
    queryKey: queryKeys.ingest.rule(workspaceId, ruleId),
    queryFn: () => getIngestRule(ruleId),
    enabled: canView,
  });

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

  const historyQuery = useQuery({
    queryKey: queryKeys.ingest.items(workspaceId, ruleId, { history: true }),
    queryFn: () => listIngestItems({ ruleId }),
    enabled: canView,
  });

  const actionMutation = useMutation({
    mutationFn: async (action: 'activate' | 'pause' | 'resume' | 'run') => {
      if (action === 'activate') return activateIngestRule(ruleId);
      if (action === 'pause') return pauseIngestRule(ruleId);
      if (action === 'resume') return resumeIngestRule(ruleId);
      return startIngestRun(ruleId);
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.ingest.all(workspaceId) });
      toast({ title: t('rules.actionSuccess') });
    },
    onError: (error) => {
      toast({
        variant: 'destructive',
        title: t('rules.errorTitle'),
        description: getErrorMessage(error),
      });
    },
  });

  if (!canView) {
    return (
      <EmptyState
        icon={<ShieldOff className="h-6 w-6" aria-hidden />}
        title={t('forbiddenTitle')}
        description={t('forbiddenBody')}
      />
    );
  }

  if (ruleQuery.isLoading || projectsQuery.isLoading || channelsQuery.isLoading) {
    return <Skeleton className="h-64 w-full" />;
  }

  const rule = ruleQuery.data;
  const project = projectsQuery.data?.items?.[0];
  if (!rule || !project) {
    return (
      <EmptyState
        title={t('notFoundTitle')}
        description={t('notFoundBody')}
      />
    );
  }

  return (
    <div className="mx-auto flex w-full max-w-5xl flex-col gap-6" data-testid="grabber-rule-detail">
      <div className="flex flex-wrap items-center gap-3">
        <Button variant="ghost" size="sm" asChild>
          <Link href={`/app/${workspaceId}/automations/grabber`}>
            <ArrowLeft className="mr-1 h-4 w-4" aria-hidden />
            {t('back')}
          </Link>
        </Button>
        <h1 className="text-2xl font-semibold">{rule.name}</h1>
        <RuleStatusBadge status={rule.status} />
      </div>

      {canManage ? (
        <div className="flex flex-wrap gap-2">
          {rule.status === 'draft' ? (
            <Button
              size="sm"
              onClick={() => actionMutation.mutate('activate')}
              data-testid="grabber-activate"
            >
              {t('rules.activate')}
            </Button>
          ) : null}
          {rule.status === 'active' ? (
            <>
              <Button
                size="sm"
                variant="outline"
                onClick={() => actionMutation.mutate('run')}
                data-testid="grabber-detail-run"
              >
                {t('rules.checkNow')}
              </Button>
              <Button size="sm" variant="outline" onClick={() => actionMutation.mutate('pause')}>
                {t('rules.pause')}
              </Button>
            </>
          ) : null}
          {rule.status === 'paused' || rule.status === 'auto_paused' ? (
            <Button size="sm" variant="outline" onClick={() => actionMutation.mutate('resume')}>
              {t('rules.resume')}
            </Button>
          ) : null}
          {rule.status !== 'archived' ? (
            <DeleteGrabberButton ruleId={ruleId} workspaceId={workspaceId} redirectToList />
          ) : null}
        </div>
      ) : null}

      <Tabs defaultValue="queue">
        <TabsList>
          <TabsTrigger value="queue">{t('tabs.queue')}</TabsTrigger>
          <TabsTrigger value="history">{t('tabs.history')}</TabsTrigger>
          <TabsTrigger value="runs">{t('tabs.runs')}</TabsTrigger>
          {canManage ? <TabsTrigger value="edit">{t('tabs.edit')}</TabsTrigger> : null}
        </TabsList>
        <TabsContent value="queue" className="mt-4">
          <ModerationQueue
            workspaceId={workspaceId}
            rules={[rule]}
            canModerate={canModerate}
          />
        </TabsContent>
        <TabsContent value="history" className="mt-4">
          <div className="space-y-4" data-testid="grabber-items-history">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <p className="text-sm text-muted-foreground">{t('export.hint')}</p>
              <HistoryExportButtons ruleId={ruleId} />
            </div>
            {(historyQuery.data?.items ?? []).map((item) => (
              <div key={item.id} className="border-b border-border py-3 text-sm">
                <p className="font-medium">{item.status}</p>
                <p className="text-muted-foreground">{item.contentText}</p>
                {item.sourcePermalink ? (
                  <a
                    className="text-primary underline-offset-2 hover:underline"
                    href={item.sourcePermalink}
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    {item.sourcePermalink}
                  </a>
                ) : null}
              </div>
            ))}
          </div>
        </TabsContent>
        <TabsContent value="runs" className="mt-4">
          <RunsHistory workspaceId={workspaceId} ruleId={ruleId} />
        </TabsContent>
        {canManage ? (
          <TabsContent value="edit" className="mt-4">
            <RuleForm
              workspaceId={workspaceId}
              project={project}
              channels={channelsQuery.data?.items ?? []}
              rule={rule}
            />
          </TabsContent>
        ) : null}
      </Tabs>
    </div>
  );
}
