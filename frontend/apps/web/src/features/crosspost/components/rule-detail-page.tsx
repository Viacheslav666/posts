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
  activateCrosspostRule,
  getCrosspostRule,
  pauseCrosspostRule,
} from '../api/crosspost-api';
import { readAutoPauseReason } from '../model/config';
import { JournalTable } from './journal-table';
import { PreviewPanel } from './preview-panel';
import { RuleForm } from './rule-form';
import { RuleStatusBadge } from './rule-status-badge';

type RuleDetailPageProps = {
  workspaceId: string;
  ruleId: string;
};

export function RuleDetailPage({ workspaceId, ruleId }: RuleDetailPageProps) {
  const t = useTranslations('automations.crosspost');
  const tPause = useTranslations('automations.crosspost.autoPause');
  const canView = useCan('automations.view', workspaceId);
  const canManage = useCan('automations.manage', workspaceId);
  const canModerate = useCan('automations.moderate', workspaceId);
  const queryClient = useQueryClient();

  const ruleQuery = useQuery({
    queryKey: queryKeys.crosspost.rule(workspaceId, ruleId),
    queryFn: () => getCrosspostRule(ruleId),
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

  const actionMutation = useMutation({
    mutationFn: async (action: 'activate' | 'pause') => {
      if (action === 'activate') return activateCrosspostRule(ruleId);
      return pauseCrosspostRule(ruleId);
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.crosspost.all(workspaceId) });
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

  const sourceChannel = channels.find((c) => c.id === rule.sourceChannelId);
  const targetChannels = channels.filter((c) =>
    (rule.targets ?? []).some((target) => target.targetChannelId === c.id),
  );
  const autoPauseReason = readAutoPauseReason(rule);

  return (
    <div className="mx-auto flex w-full max-w-5xl flex-col gap-6" data-testid="crosspost-rule-detail">
      <div className="flex flex-wrap items-center gap-3">
        <Button variant="ghost" size="sm" asChild>
          <Link href={`/app/${workspaceId}/automations/crosspost`}>
            <ArrowLeft className="mr-1 h-4 w-4" aria-hidden />
            {t('back')}
          </Link>
        </Button>
        <h1 className="text-2xl font-semibold">{rule.name}</h1>
        <RuleStatusBadge status={rule.status} />
      </div>

      {rule.status === 'auto_paused' && autoPauseReason ? (
        <p className="text-sm text-destructive" data-testid="crosspost-detail-auto-pause">
          {autoPauseReason === 'circuit_volume' || autoPauseReason === 'circuit_loop_skips'
            ? tPause(autoPauseReason)
            : autoPauseReason}
        </p>
      ) : null}

      {(rule.warnings ?? []).some((w) => w.code === 'cycle_detected') ? (
        <div
          role="status"
          className="rounded-md border border-amber-500/40 bg-amber-500/10 px-3 py-2 text-sm"
          data-testid="crosspost-detail-cycle-warning"
        >
          {t('form.cycleBody')}
        </div>
      ) : null}

      {canManage ? (
        <div className="flex flex-wrap gap-2">
          {rule.status === 'draft' || rule.status === 'paused' || rule.status === 'auto_paused' ? (
            <Button
              size="sm"
              onClick={() => actionMutation.mutate('activate')}
              data-testid="crosspost-detail-activate"
            >
              {t('rules.activate')}
            </Button>
          ) : null}
          {rule.status === 'active' ? (
            <Button
              size="sm"
              variant="outline"
              onClick={() => actionMutation.mutate('pause')}
              data-testid="crosspost-detail-pause"
            >
              {t('rules.pause')}
            </Button>
          ) : null}
        </div>
      ) : null}

      <Tabs defaultValue="settings">
        <TabsList>
          <TabsTrigger value="settings" data-testid="crosspost-tab-settings">
            {t('tabs.settings')}
          </TabsTrigger>
          <TabsTrigger value="preview" data-testid="crosspost-tab-preview">
            {t('tabs.preview')}
          </TabsTrigger>
          <TabsTrigger value="journal" data-testid="crosspost-tab-journal">
            {t('tabs.journal')}
          </TabsTrigger>
        </TabsList>

        <TabsContent value="settings" className="mt-4">
          {canManage ? (
            <RuleForm
              workspaceId={workspaceId}
              project={project}
              channels={channels}
              rule={rule}
              initialWarnings={rule.warnings}
            />
          ) : (
            <EmptyState title={t('forbiddenTitle')} description={t('forbiddenManageBody')} />
          )}
        </TabsContent>

        <TabsContent value="preview" className="mt-4">
          <PreviewPanel ruleId={ruleId} targets={targetChannels} />
        </TabsContent>

        <TabsContent value="journal" className="mt-4">
          <JournalTable
            workspaceId={workspaceId}
            ruleId={ruleId}
            sourceChannel={sourceChannel}
            canModerate={canModerate}
          />
        </TabsContent>
      </Tabs>
    </div>
  );
}
