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
  activateAntispamRule,
  getAntispamRule,
  listAntispamRules,
  pauseAntispamRule,
} from '../api/antispam-api';
import { HistoryTable } from './history-table';
import { RuleForm } from './rule-form';
import { RuleStatusBadge } from './rule-status-badge';
import { ScanPanel } from './scan-panel';
import { WordListsPanel } from './word-lists-panel';

type RuleDetailPageProps = {
  workspaceId: string;
  ruleId: string;
};

export function RuleDetailPage({ workspaceId, ruleId }: RuleDetailPageProps) {
  const t = useTranslations('automations.antispam');
  const canView = useCan('automations.view', workspaceId);
  const canManage = useCan('automations.manage', workspaceId);
  const canModerate = useCan('automations.moderate', workspaceId);
  const queryClient = useQueryClient();

  const ruleQuery = useQuery({
    queryKey: queryKeys.antispam.rule(workspaceId, ruleId),
    queryFn: () => getAntispamRule(ruleId),
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

  const projectId = projectsQuery.data?.items?.[0]?.id;
  const rulesQuery = useQuery({
    queryKey: queryKeys.antispam.rules(workspaceId, projectId ?? ''),
    queryFn: () => listAntispamRules({ workspaceId, projectId: projectId! }),
    enabled: canView && Boolean(projectId),
  });

  const actionMutation = useMutation({
    mutationFn: async (action: 'activate' | 'pause') => {
      if (action === 'activate') return activateAntispamRule(ruleId);
      return pauseAntispamRule(ruleId);
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.antispam.all(workspaceId) });
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
  const channels = channelsQuery.data?.items ?? [];
  if (!rule || !project) {
    return <EmptyState title={t('notFoundTitle')} description={t('notFoundBody')} />;
  }

  const channel = channels.find((c) => c.id === rule.channelId);

  return (
    <div className="mx-auto flex w-full max-w-5xl flex-col gap-6" data-testid="antispam-rule-detail">
      <div className="flex flex-wrap items-center gap-3">
        <Button variant="ghost" size="sm" asChild>
          <Link href={`/app/${workspaceId}/automations/antispam`}>
            <ArrowLeft className="mr-1 h-4 w-4" aria-hidden />
            {t('back')}
          </Link>
        </Button>
        <h1 className="text-2xl font-semibold">{rule.name}</h1>
        <RuleStatusBadge status={rule.status} />
        {rule.dryRun ? (
          <span className="rounded-md border border-border px-1.5 py-0.5 text-xs text-muted-foreground">
            {t('rules.dryRunBadge')}
          </span>
        ) : null}
      </div>

      {canManage ? (
        <div className="flex flex-wrap gap-2">
          {rule.status === 'draft' || rule.status === 'paused' ? (
            <Button
              size="sm"
              onClick={() => actionMutation.mutate('activate')}
              data-testid="antispam-detail-activate"
            >
              {t('rules.activate')}
            </Button>
          ) : null}
          {rule.status === 'active' ? (
            <Button
              size="sm"
              variant="outline"
              onClick={() => actionMutation.mutate('pause')}
              data-testid="antispam-detail-pause"
            >
              {t('rules.pause')}
            </Button>
          ) : null}
        </div>
      ) : null}

      <Tabs defaultValue="settings">
        <TabsList>
          <TabsTrigger value="settings" data-testid="antispam-tab-settings">
            {t('tabs.settings')}
          </TabsTrigger>
          <TabsTrigger value="dictionaries" data-testid="antispam-tab-dictionaries">
            {t('tabs.dictionaries')}
          </TabsTrigger>
          <TabsTrigger value="history" data-testid="antispam-tab-history">
            {t('tabs.history')}
          </TabsTrigger>
          <TabsTrigger value="scan" data-testid="antispam-tab-scan">
            {t('tabs.scan')}
          </TabsTrigger>
        </TabsList>

        <TabsContent value="settings" className="mt-4">
          {canManage ? (
            <RuleForm
              workspaceId={workspaceId}
              project={project}
              channels={channels}
              rule={rule}
            />
          ) : (
            <EmptyState title={t('forbiddenTitle')} description={t('forbiddenManageBody')} />
          )}
        </TabsContent>

        <TabsContent value="dictionaries" className="mt-4">
          <WordListsPanel
            workspaceId={workspaceId}
            rules={rulesQuery.data?.items ?? [rule]}
            canManage={canManage}
          />
        </TabsContent>

        <TabsContent value="history" className="mt-4">
          <HistoryTable
            workspaceId={workspaceId}
            ruleId={ruleId}
            channels={channels}
            canModerate={canModerate}
          />
        </TabsContent>

        <TabsContent value="scan" className="mt-4">
          <ScanPanel ruleId={ruleId} channel={channel} canModerate={canModerate} />
        </TabsContent>
      </Tabs>
    </div>
  );
}
