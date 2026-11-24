'use client';

import type { Channel, CrosspostItem } from '@posts/api-client';
import { Button, EmptyState, Skeleton, toast } from '@posts/ui';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { format } from 'date-fns';
import { ru } from 'date-fns/locale';
import Link from 'next/link';
import { useTranslations } from 'next-intl';
import { useEffect, useState } from 'react';

import { ReasonChips } from '@/features/automations-common';
import { queryKeys } from '@/shared/api-client';
import { getErrorMessage } from '@/shared/lib/errors';

import { listCrosspostItems, retryCrosspostItem } from '../api/crosspost-api';
import { buildSourcePermalink } from '../model/permalink';
import type { ItemStatus } from '../model/types';
import { ITEM_STATUSES } from '../model/types';

type JournalTableProps = {
  workspaceId: string;
  ruleId: string;
  sourceChannel?: Channel;
  canModerate: boolean;
};

export function JournalTable({
  workspaceId,
  ruleId,
  sourceChannel,
  canModerate,
}: JournalTableProps) {
  const t = useTranslations('automations.crosspost.journal');
  const tStatus = useTranslations('automations.crosspost.itemStatus');
  const queryClient = useQueryClient();
  const [statusFilter, setStatusFilter] = useState<string>('all');
  const [items, setItems] = useState<CrosspostItem[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [loadingMore, setLoadingMore] = useState(false);

  const statusQuery = statusFilter === 'all' ? undefined : statusFilter;

  const itemsQuery = useQuery({
    queryKey: queryKeys.crosspost.items(workspaceId, ruleId, { status: statusQuery }),
    queryFn: () =>
      listCrosspostItems({
        ruleId,
        status: statusQuery,
      }),
  });

  useEffect(() => {
    if (!itemsQuery.data) return;
    setItems(itemsQuery.data.items ?? []);
    setNextCursor(itemsQuery.data.nextCursor ?? null);
  }, [itemsQuery.data]);

  const loadMore = async () => {
    if (!nextCursor || loadingMore) return;
    setLoadingMore(true);
    try {
      const page = await listCrosspostItems({
        ruleId,
        status: statusQuery,
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

  const retryMutation = useMutation({
    mutationFn: (itemId: string) => retryCrosspostItem(itemId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: queryKeys.crosspost.all(workspaceId),
      });
      toast({ title: t('retrySuccess') });
    },
    onError: (error) => {
      toast({
        variant: 'destructive',
        title: t('errorTitle'),
        description: getErrorMessage(error),
      });
    },
  });

  if (itemsQuery.isLoading && items.length === 0) {
    return <Skeleton className="h-40 w-full" data-testid="crosspost-journal-loading" />;
  }

  return (
    <div className="space-y-4" data-testid="crosspost-journal">
      <p className="text-xs text-muted-foreground">{t('aggregateHint')}</p>
      <div className="flex flex-wrap items-end gap-3">
        <div className="space-y-1">
          <label htmlFor="crosspost-journal-status" className="text-sm font-medium">
            {t('statusFilter')}
          </label>
          <select
            id="crosspost-journal-status"
            className="flex h-10 rounded-md border border-input bg-background px-3 text-sm"
            value={statusFilter}
            onChange={(event) => setStatusFilter(event.target.value)}
            data-testid="crosspost-journal-status-filter"
          >
            <option value="all">{t('allStatuses')}</option>
            {ITEM_STATUSES.map((status) => (
              <option key={status} value={status}>
                {tStatus(status)}
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
            <table className="w-full min-w-[640px] border-collapse text-sm">
              <thead>
                <tr className="border-b border-border text-left text-muted-foreground">
                  <th className="px-2 py-2 font-medium">{t('columns.time')}</th>
                  <th className="px-2 py-2 font-medium">{t('columns.source')}</th>
                  <th className="px-2 py-2 font-medium">{t('columns.status')}</th>
                  <th className="px-2 py-2 font-medium">{t('columns.reasons')}</th>
                  <th className="px-2 py-2 font-medium">{t('columns.actions')}</th>
                </tr>
              </thead>
              <tbody>
                {items.map((item) => (
                  <JournalRow
                    key={item.id}
                    item={item}
                    workspaceId={workspaceId}
                    sourceChannel={sourceChannel}
                    canModerate={canModerate}
                    onRetry={() => item.id && retryMutation.mutate(item.id)}
                    retryPending={retryMutation.isPending}
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
              data-testid="crosspost-journal-load-more"
            >
              {loadingMore ? t('loadingMore') : t('loadMore')}
            </Button>
          ) : null}
        </div>
      )}
    </div>
  );
}

function JournalRow({
  item,
  workspaceId,
  sourceChannel,
  canModerate,
  onRetry,
  retryPending,
}: {
  item: CrosspostItem;
  workspaceId: string;
  sourceChannel?: Channel;
  canModerate: boolean;
  onRetry: () => void;
  retryPending: boolean;
}) {
  const t = useTranslations('automations.crosspost.journal');
  const tStatus = useTranslations('automations.crosspost.itemStatus');
  const stamp = item.createdAt
    ? format(new Date(item.createdAt), 'dd MMM HH:mm', { locale: ru })
    : '-';
  const permalink = buildSourcePermalink(item.sourceNetwork, item.sourceRemoteId, sourceChannel);
  const status = (item.status ?? 'received') as ItemStatus;

  return (
    <tr
      className="border-b border-border align-top"
      data-testid={`crosspost-journal-item-${item.id}`}
    >
      <td className="px-2 py-3 whitespace-nowrap text-muted-foreground">{stamp}</td>
      <td className="px-2 py-3">
        <div className="space-y-1">
          {permalink ? (
            <a
              href={permalink}
              target="_blank"
              rel="noopener noreferrer"
              className="text-primary underline-offset-2 hover:underline"
              data-testid={`crosspost-source-link-${item.id}`}
            >
              {item.sourceRemoteId ?? permalink}
            </a>
          ) : (
            <span>{item.sourceRemoteId ?? '-'}</span>
          )}
          {item.postId ? (
            <div>
              <Link
                href={`/app/${workspaceId}/posts/${item.postId}`}
                className="text-xs text-muted-foreground underline-offset-2 hover:underline"
                data-testid={`crosspost-post-link-${item.id}`}
              >
                {t('openPost')}
              </Link>
            </div>
          ) : null}
        </div>
      </td>
      <td className="px-2 py-3">
        <span data-status={status}>{tStatus(status)}</span>
      </td>
      <td className="px-2 py-3">
        <ReasonChips codes={item.reasons ?? []} namespace="automations.crosspost.reasons" />
      </td>
      <td className="px-2 py-3">
        {canModerate && status === 'failed' ? (
          <Button
            size="sm"
            variant="outline"
            disabled={retryPending}
            onClick={onRetry}
            data-testid={`crosspost-retry-${item.id}`}
          >
            {t('retry')}
          </Button>
        ) : (
          <span className="text-muted-foreground">-</span>
        )}
      </td>
    </tr>
  );
}
