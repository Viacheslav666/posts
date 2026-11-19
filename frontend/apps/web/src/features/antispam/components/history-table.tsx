'use client';

import type { AntispamLogEntry, Channel } from '@posts/api-client';
import { Button, EmptyState, Skeleton, toast } from '@posts/ui';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { format } from 'date-fns';
import { ru } from 'date-fns/locale';
import { useTranslations } from 'next-intl';
import { useEffect, useState } from 'react';

import { queryKeys } from '@/shared/api-client';
import { getErrorMessage } from '@/shared/lib/errors';

import { listAntispamLog, restoreAntispamLog } from '../api/antispam-api';
import { isKnownLogState, parseAntispamReason } from '../model/reasons';
import { belongsToWorkspace } from '../model/scope';
import { ALL_SECTIONS, ANTISPAM_ACTIONS, type AttachmentKey } from '../model/types';

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
    setItems(
      (logQuery.data.items ?? []).filter((entry) => belongsToWorkspace(entry, workspaceId)),
    );
    setNextCursor(logQuery.data.nextCursor ?? null);
  }, [logQuery.data, workspaceId]);

  const loadMore = async () => {
    if (!nextCursor || loadingMore) return;
    setLoadingMore(true);
    try {
      const page = await listAntispamLog({
        workspaceId,
        ...filters,
        cursor: nextCursor,
      });
      setItems((prev) => [
        ...prev,
        ...(page.items ?? []).filter((entry) => belongsToWorkspace(entry, workspaceId)),
      ]);
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

  if (logQuery.isError && items.length === 0) {
    return (
      <EmptyState
        role="alert"
        title={t('errorTitle')}
        description={getErrorMessage(logQuery.error)}
        action={
          <Button type="button" variant="outline" onClick={() => void logQuery.refetch()}>
            {t('retry')}
          </Button>
        }
      />
    );
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
            {channels.filter((channel) => belongsToWorkspace(channel, workspaceId)).map((channel) => (
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

      {logQuery.isError ? (
        <p role="alert" className="text-sm text-destructive">
          {t('errorTitle')}: {getErrorMessage(logQuery.error)}
        </p>
      ) : null}

      {items.length === 0 ? (
        <EmptyState title={t('emptyTitle')} description={t('emptyBody')} />
      ) : (
        <div className="space-y-3">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[720px] border-collapse text-sm">
              <thead>
                <tr className="border-b border-border text-left text-muted-foreground">
                  <th scope="col" className="px-2 py-2 font-medium">{t('columns.time')}</th>
                  <th scope="col" className="px-2 py-2 font-medium">{t('columns.channel')}</th>
                  <th scope="col" className="px-2 py-2 font-medium">{t('columns.text')}</th>
                  <th scope="col" className="px-2 py-2 font-medium">{t('columns.section')}</th>
                  <th scope="col" className="px-2 py-2 font-medium">{t('columns.action')}</th>
                  <th scope="col" className="px-2 py-2 font-medium">{t('columns.reasons')}</th>
                  <th scope="col" className="px-2 py-2 font-medium">{t('columns.actions')}</th>
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
  const tReason = useTranslations('automations.antispam.reasons');
  const tAttach = useTranslations('automations.antispam.attachments');
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
        <p className="max-w-[10rem] truncate" title={channel?.title}>
          {channel?.title ?? entry.channelId ?? '—'}
        </p>
      </td>
      <td className="px-2 py-3">
        <p className="max-w-xs truncate" title={entry.textSnapshot}>
          {entry.textSnapshot || '—'}
        </p>
        <p className="mt-0.5 text-xs text-muted-foreground">{entry.authorRef}</p>
      </td>
      <td className="px-2 py-3">
        {entry.section && ALL_SECTIONS.includes(entry.section as (typeof ALL_SECTIONS)[number])
          ? tSection(entry.section as 'wall_comment')
          : (entry.section ?? '—')}
      </td>
      <td className="px-2 py-3">
        <div className="space-y-0.5">
          <span>
            {entry.action && ANTISPAM_ACTIONS.includes(entry.action as (typeof ANTISPAM_ACTIONS)[number])
              ? tAction(entry.action as 'deleted')
              : (entry.action ?? '—')}
          </span>
          <p
            className="text-xs text-muted-foreground"
            data-testid={`antispam-log-state-${entry.id}`}
          >
            {isKnownLogState(entry.state) ? tState(entry.state) : (entry.state ?? '')}
          </p>
        </div>
      </td>
      <td className="px-2 py-3">
        <ReasonList
          codes={entry.reasons ?? []}
          tReason={tReason}
          tAttach={tAttach}
        />
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

function ReasonList({
  codes,
  tReason,
  tAttach,
}: {
  codes: string[];
  tReason: (key: string, values?: Record<string, string>) => string;
  tAttach: (key: AttachmentKey) => string;
}) {
  if (codes.length === 0) return null;
  return (
    <ul className="flex flex-wrap gap-1.5" data-testid="reason-chips" aria-label={tReason('label')}>
      {codes.map((code) => {
        const view = parseAntispamReason(code);
        let label = code;
        if (view.kind === 'key') label = tReason(view.key);
        else if (view.kind === 'keyword') label = tReason('keywordWithWord', { word: view.word });
        else if (view.kind === 'attachment') label = tAttach(view.attachment);
        else label = view.text;
        return (
          <li key={code}>
            <span
              className="inline-flex rounded-md border border-border px-1.5 py-0.5 text-xs"
              data-reason-code={code}
            >
              {label}
            </span>
          </li>
        );
      })}
    </ul>
  );
}
