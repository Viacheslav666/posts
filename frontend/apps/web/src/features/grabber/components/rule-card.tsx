'use client';

import type { Channel, GrabberRule } from '@posts/api-client';
import { Button, toast } from '@posts/ui';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { useTranslations } from 'next-intl';

import { queryKeys } from '@/shared/api-client';
import { getErrorMessage, isApiProblemError } from '@/shared/lib/errors';

import {
  archiveIngestRule,
  pauseIngestRule,
  resumeIngestRule,
  startIngestRun,
} from '../api/grabber-api';
import { RuleStatusBadge } from './rule-status-badge';

type RuleCardProps = {
  rule: GrabberRule;
  workspaceId: string;
  channel?: Channel;
  canManage: boolean;
};

export function RuleCard({ rule, workspaceId, channel, canManage }: RuleCardProps) {
  const t = useTranslations('grabber.rules');
  const tTypes = useTranslations('grabber.types');
  const queryClient = useQueryClient();

  const invalidate = async () => {
    await queryClient.invalidateQueries({ queryKey: queryKeys.ingest.all(workspaceId) });
  };

  const actionMutation = useMutation({
    mutationFn: async (action: 'pause' | 'resume' | 'archive' | 'run') => {
      const id = rule.id!;
      if (action === 'pause') return pauseIngestRule(id);
      if (action === 'resume') return resumeIngestRule(id);
      if (action === 'archive') return archiveIngestRule(id);
      return startIngestRun(id);
    },
    onSuccess: async (_data, action) => {
      await invalidate();
      toast({
        title:
          action === 'run'
            ? t('runSuccess')
            : action === 'pause'
              ? t('pauseSuccess')
              : action === 'resume'
                ? t('resumeSuccess')
                : t('archiveSuccess'),
      });
    },
    onError: (error) => {
      const description =
        isApiProblemError(error) && error.code === 'ingest_rate_limited'
          ? t('rateLimited')
          : getErrorMessage(error);
      toast({
        variant: 'destructive',
        title: t('errorTitle'),
        description,
      });
    },
  });

  const sources =
    rule.type === 'rss'
      ? rule.config?.rssUrl
      : rule.type === 'website'
        ? (rule.config?.websiteUrls ?? []).join(', ')
        : (rule.config?.sourceIds ?? []).join(', ');

  return (
    <article
      data-testid={`grabber-rule-${rule.id}`}
      className="flex flex-col gap-3 border-b border-border py-4 last:border-b-0 sm:flex-row sm:items-start sm:justify-between"
    >
      <div className="min-w-0 space-y-1">
        <div className="flex flex-wrap items-center gap-2">
          <Link
            href={`/app/${workspaceId}/automations/grabber/${rule.id}`}
            className="text-base font-semibold text-foreground hover:underline"
          >
            {rule.name}
          </Link>
          <RuleStatusBadge status={rule.status} />
          <span className="text-xs text-muted-foreground">{tTypes(rule.type ?? 'grabber')}</span>
        </div>
        <p className="text-sm text-muted-foreground">
          {t('target')}: {channel?.title ?? rule.targetChannelId}
        </p>
        <p className="truncate text-sm text-muted-foreground">
          {t('sources')}: {sources || '—'}
        </p>
        <p className="text-xs text-muted-foreground">
          {t('mode')}: {rule.config?.publishMode ?? 'moderate'} · {t('copied')}:{' '}
          {rule.copiedTotal ?? 0}
        </p>
      </div>
      {canManage ? (
        <div className="flex flex-wrap gap-2">
          <Button
            size="sm"
            variant="outline"
            disabled={actionMutation.isPending || rule.status !== 'active'}
            onClick={() => actionMutation.mutate('run')}
            data-testid={`grabber-run-${rule.id}`}
          >
            {t('checkNow')}
          </Button>
          {rule.status === 'active' ? (
            <Button
              size="sm"
              variant="outline"
              disabled={actionMutation.isPending}
              onClick={() => actionMutation.mutate('pause')}
            >
              {t('pause')}
            </Button>
          ) : rule.status === 'paused' || rule.status === 'auto_paused' ? (
            <Button
              size="sm"
              variant="outline"
              disabled={actionMutation.isPending}
              onClick={() => actionMutation.mutate('resume')}
            >
              {t('resume')}
            </Button>
          ) : null}
          <Button size="sm" variant="ghost" asChild>
            <Link href={`/app/${workspaceId}/automations/grabber/${rule.id}`}>
              {t('open')}
            </Link>
          </Button>
        </div>
      ) : (
        <Button size="sm" variant="ghost" asChild>
          <Link href={`/app/${workspaceId}/automations/grabber/${rule.id}`}>{t('open')}</Link>
        </Button>
      )}
    </article>
  );
}
