'use client';

import type { AntispamLogEntry, Channel } from '@posts/api-client';
import { Button, EmptyState, Skeleton, toast } from '@posts/ui';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { format } from 'date-fns';
import { ru } from 'date-fns/locale';
import { useTranslations } from 'next-intl';
import { useEffect, useState } from 'react';

import { ReasonChips } from '@/features/automations-common';
import { queryKeys } from '@/shared/api-client';
import { getErrorMessage } from '@/shared/lib/errors';

import { listAntispamLog, restoreAntispamLog } from '../api/antispam-api';
import { ALL_SECTIONS, ANTISPAM_ACTIONS } from '../model/types';

type HistoryTableProps = {
  workspaceId: string;
  ruleId?: string;
  channels: Channel[];
  canModerate: boolean;
};

export function HistoryTable({
  workspaceId,
  ruleId,
  channels,
  canModerate,
}: HistoryTableProps) {
  const t = useTranslations('automations.antispam.history');
  const tAction = useTranslations('automations.antispam.actions');
  const tSection = useTranslations('automations.antispam.sections');
  const queryClient = useQueryClient();
  const [channelFilter, setChannelFilter] = useState('all');
  const [sectionFilter, setSectionFilter] = useState('all');
  const [actionFilter, setActionFilter] = useState('all');
  const [items, setItems] = useState<AntispamLogEntry[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [loadingMore, setLoadingMore] = useState(false);

  const filters = {
    ruleId,
    channelId: channelFilter === 'all' ? undefined : channelFilter,
    section: sectionFilter === 'all' ? undefined : sectionFilter,
    action: actionFilter === 'all' ? undefined : actionFilter,
  };

  const logQuery = useQuery({
    queryKey: queryKeys.antispam.log(workspaceId, filters),
    queryFn: () =>
      listAntispamLog({
        workspaceId,
        ...filters,
      }),
  });

  useEffect(() => {
    if (!logQuery.data) return;
    setItems(logQuery.data.items ?? []);
    setNextCursor(logQuery.data.nextCursor ?? null);
  }, [logQuery.data]);

  const loadMore = async () => {
    if (!nextCursor || loadingMore) return;
    setLoadingMore(true);
    try {
      const page = await listAntispamLog({
        workspaceId,
        ...filters,
        cursor: nextCursor,
      });
      setItems((prev) => [...prev, ...(page.items ?? [])]);
      setNextCursor(page.nextCursor ?? null);
    } catch (error) {
      toast({
        variant: 'destructive',
        title: t('errorTitle'),
        description: getErrorMessage(error),
      });
    } finally {
      setLoadingMore(false);
    }
  };

  const restoreMutation = useMutation({
    mutationFn: ({ logId, unban }: { logId: string; unban: boolean }) =>
      restoreAntispamLog(logId, { unban }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: queryKeys.antispam.all(workspaceId),
      });
      toast({ title: t('restoreSuccess') });
    },
    onError: (error) => {
      toast({
        variant: 'destructive',
        title: t('errorTitle'),
        description: getErrorMessage(error),
      });
    },
  });

  if (logQuery.isLoading && items.length === 0) {
    return <Skeleton className="h-40 w-full" data-testid="antispam-history-loading" />;
  }

  return (
    <div className="space-y-4" data-testid="antispam-history">
      <div className="flex flex-wrap items-end gap-3">
        <div className="space-y-1">
          <label htmlFor="antispam-history-channel" className="text-sm font-medium">
            {t('channelFilter')}
          </label>
          <select
            id="antispam-history-channel"
            className="flex h-10 rounded-md border border-input bg-background px-3 text-sm"
            value={channelFilter}
            onChange={(event) => setChannelFilter(event.target.value)}
            data-testid="antispam-history-channel-filter"
          >
            <option value="all">{t('allChannels')}</option>
            {channels.map((channel) => (
              <option key={channel.id} value={channel.id}>
                {channel.title}
              </option>
            ))}
          </select>
        </div>
        <div className="space-y-1">
          <label htmlFor="antispam-history-section" className="text-sm font-medium">
            {t('sectionFilter')}
          </label>
          <select
            id="antispam-history-section"
            className="flex h-10 rounded-md border border-input bg-background px-3 text-sm"
            value={sectionFilter}
            onChange={(event) => setSectionFilter(event.target.value)}
            data-testid="antispam-history-section-filter"
          >
            <option value="all">{t('allSections')}</option>
            {ALL_SECTIONS.map((section) => (
              <option key={section} value={section}>
                {tSection(section)}
              </option>
            ))}
          </select>
        </div>
        <div className="space-y-1">
          <label htmlFor="antispam-history-action" className="text-sm font-medium">
            {t('actionFilter')}
          </label>
          <select
            id="antispam-history-action"
            className="flex h-10 rounded-md border border-input bg-background px-3 text-sm"
            value={actionFilter}
            onChange={(event) => setActionFilter(event.target.value)}
            data-testid="antispam-history-action-filter"
          >
            <option value="all">{t('allActions')}</option>
            {ANTISPAM_ACTIONS.map((action) => (
              <option key={action} value={action}>
                {tAction(action)}
              </option>
            ))}
          </select>
        </div>
      </div>

      {items.length === 0 ? (
        <EmptyState title={t('emptyTitle')} description={t('emptyBody')} />
      ) : (
        <div className="space-y-3">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[720px] border-collapse text-sm">
              <thead>
                <tr className="border-b border-border text-left text-muted-foreground">
                  <th className="px-2 py-2 font-medium">{t('columns.time')}</th>
                  <th className="px-2 py-2 font-medium">{t('columns.text')}</th>
                  <th className="px-2 py-2 font-medium">{t('columns.section')}</th>
                  <th className="px-2 py-2 font-medium">{t('columns.action')}</th>
                  <th className="px-2 py-2 font-medium">{t('columns.reasons')}</th>
                  <th className="px-2 py-2 font-medium">{t('columns.actions')}</th>
                </tr>
              </thead>
              <tbody>
                {items.map((entry) => (
                  <HistoryRow
                    key={entry.id}
                    entry={entry}
                    canModerate={canModerate}
                    channel={channels.find((c) => c.id === entry.channelId)}
                    onRestore={(unban) =>
                      entry.id && restoreMutation.mutate({ logId: entry.id, unban })
                    }
                    restorePending={restoreMutation.isPending}
                  />
                ))}
              </tbody>
            </table>
          </div>
          {nextCursor ? (
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={loadingMore}
              onClick={() => void loadMore()}
              data-testid="antispam-history-load-more"
            >
              {loadingMore ? t('loadingMore') : t('loadMore')}
            </Button>
          ) : null}
        </div>
      )}
    </div>
  );
}

function HistoryRow({
  entry,
  channel,
  canModerate,
  onRestore,
  restorePending,
}: {
  entry: AntispamLogEntry;
  channel?: Channel;
  canModerate: boolean;
  onRestore: (unban: boolean) => void;
  restorePending: boolean;
}) {
  const t = useTranslations('automations.antispam.history');
  const tAction = useTranslations('automations.antispam.actions');
  const tSection = useTranslations('automations.antispam.sections');
  const tState = useTranslations('automations.antispam.logState');
  const [unban, setUnban] = useState(false);
  const stamp = entry.createdAt
    ? format(new Date(entry.createdAt), 'dd MMM HH:mm', { locale: ru })
    : '—';
  const isRestored = entry.state === 'restored';
  const network = channel?.network;

  return (
    <tr
      className="border-b border-border align-top"
      data-testid={`antispam-history-item-${entry.id}`}
    >
      <td className="px-2 py-3 whitespace-nowrap text-muted-foreground">{stamp}</td>
      <td className="px-2 py-3">
        <p className="max-w-xs truncate" title={entry.textSnapshot}>
          {entry.textSnapshot || '—'}
        </p>
        <p className="mt-0.5 text-xs text-muted-foreground">{entry.authorRef}</p>
      </td>
      <td className="px-2 py-3">
        {entry.section ? tSection(entry.section as 'wall_comment') : '—'}
      </td>
      <td className="px-2 py-3">
        <div className="space-y-0.5">
          <span>{entry.action ? tAction(entry.action as 'deleted') : '—'}</span>
          <p className="text-xs text-muted-foreground">
            {entry.state ? tState(entry.state as 'done') : ''}
          </p>
        </div>
      </td>
      <td className="px-2 py-3">
        <ReasonChips codes={entry.reasons ?? []} namespace="automations.antispam.reasons" />
      </td>
      <td className="px-2 py-3">
        {canModerate && entry.restorable && !isRestored ? (
          <div className="space-y-2">
            <label className="inline-flex items-center gap-2 text-xs">
              <input
                type="checkbox"
                checked={unban}
                onChange={(event) => setUnban(event.target.checked)}
                data-testid={`antispam-unban-${entry.id}`}
              />
              {t('unban')}
            </label>
            <Button
              size="sm"
              variant="outline"
              disabled={restorePending}
              onClick={() => onRestore(unban)}
              data-testid={`antispam-restore-${entry.id}`}
            >
              {t('restore')}
            </Button>
          </div>
        ) : null}
        {canModerate && !entry.restorable && !isRestored ? (
          <p
            className="max-w-[12rem] text-xs text-muted-foreground"
            data-testid={`antispam-restore-unavailable-${entry.id}`}
          >
            {network === 'tg' || network === 'max' ? t('restoreUnavailableTgMax') : t('restoreUnavailable')}
          </p>
        ) : null}
        {isRestored ? (
          <span className="text-xs text-muted-foreground" data-testid={`antispam-restored-${entry.id}`}>
            {tState('restored')}
          </span>
        ) : null}
      </td>
    </tr>
  );
}
