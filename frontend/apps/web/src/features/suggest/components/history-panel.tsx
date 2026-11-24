'use client';

import type { SuggestItem, SuggestSettings } from '@posts/api-client';
import {
  Button,
  EmptyState,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Skeleton,
  toast,
} from '@posts/ui';
import { useInfiniteQuery } from '@tanstack/react-query';
import { History } from 'lucide-react';
import { useMemo, useState } from 'react';
import { useTranslations } from 'next-intl';

import { ReasonChips } from '@/features/automations-common';
import { queryKeys } from '@/shared/api-client';
import { getErrorMessage } from '@/shared/lib/errors';

import { listAllSuggestItems, listSuggestItems } from '../api/suggest-api';
import { suggestItemInScope } from '../model/scope';
import { HISTORY_FILTERS, HISTORY_STATUSES, SUGGEST_PAGE_SIZE, type ItemStatus } from '../model/types';

type HistoryPanelProps = {
  workspaceId: string;
  settings: SuggestSettings;
};

function toCsv(items: SuggestItem[]): string {
  const header = [
    'id',
    'vk_post_id',
    'author_vk_id',
    'status',
    'reasons',
    'created_at',
    'decided_at',
    'text',
  ];
  const rows = items.map((item) =>
    [
      csvEscape(item.id ?? ''),
      csvEscape(item.vkPostId ?? ''),
      csvEscape(item.authorVkId ?? ''),
      csvEscape(item.status ?? ''),
      csvEscape((item.reasons ?? []).join('|')),
      csvEscape(item.createdAt ?? ''),
      csvEscape(item.decidedAt ?? ''),
      csvEscape(item.text ?? ''),
    ].join(','),
  );
  return [header.join(','), ...rows].join('\n');
}

function csvEscape(value: string): string {
  if (/[",\n]/.test(value)) {
    return `"${value.replace(/"/g, '""')}"`;
  }
  return value;
}

type HistoryCursor = {
  cursors: Partial<Record<ItemStatus, string | undefined>>;
  done: ItemStatus[];
};

type HistoryPage = {
  items: SuggestItem[];
  hasMore: boolean;
  nextCursor?: string;
  allCursor?: HistoryCursor;
};

export function HistoryPanel({ workspaceId, settings }: HistoryPanelProps) {
  const t = useTranslations('automations.suggest.history');
  const settingsId = settings.id!;
  const [statusFilter, setStatusFilter] = useState<ItemStatus | 'all'>('published');
  const [exporting, setExporting] = useState(false);
  const channelId = settings.channelId;

  const itemsQuery = useInfiniteQuery({
    queryKey: queryKeys.suggest.items(workspaceId, settingsId, {
      history: true,
      status: statusFilter === 'all' ? undefined : statusFilter,
    }),
    initialPageParam: undefined as string | HistoryCursor | undefined,
    queryFn: async ({ pageParam }): Promise<HistoryPage> => {
      if (statusFilter !== 'all') {
        const cursor = typeof pageParam === 'string' ? pageParam : undefined;
        const page = await listSuggestItems({
          settingsId,
          status: statusFilter,
          cursor,
          limit: SUGGEST_PAGE_SIZE,
        });
        return {
          items: page.items ?? [],
          hasMore: Boolean(page.hasMore && page.nextCursor),
          nextCursor: page.nextCursor,
        };
      }
      const cursor: HistoryCursor =
        pageParam && typeof pageParam === 'object' ? pageParam : { cursors: {}, done: [] };
      const pages = await Promise.all(
        HISTORY_STATUSES.map(async (status) => {
          if (cursor.done.includes(status)) {
            return { status, items: [] as SuggestItem[], hasMore: false, nextCursor: undefined };
          }
          const page = await listSuggestItems({
            settingsId,
            status,
            cursor: cursor.cursors[status],
            limit: SUGGEST_PAGE_SIZE,
          });
          return {
            status,
            items: page.items ?? [],
            hasMore: Boolean(page.hasMore && page.nextCursor),
            nextCursor: page.nextCursor,
          };
        }),
      );
      const done = [
        ...cursor.done,
        ...pages.filter((page) => !page.hasMore).map((page) => page.status),
      ];
      const cursors: HistoryCursor['cursors'] = { ...cursor.cursors };
      for (const page of pages) {
        if (page.hasMore) cursors[page.status] = page.nextCursor;
      }
      return {
        items: pages.flatMap((page) => page.items),
        hasMore: pages.some((page) => page.hasMore),
        allCursor: { cursors, done },
      };
    },
    getNextPageParam: (last) => {
      if (!last.hasMore) return undefined;
      if (last.allCursor) return last.allCursor;
      return last.nextCursor;
    },
  });

  const items = useMemo(
    () =>
      (itemsQuery.data?.pages ?? [])
        .flatMap((page) => page.items)
        .filter((item) => suggestItemInScope(item, { workspaceId, settingsId, channelId })),
    [itemsQuery.data?.pages, workspaceId, settingsId, channelId],
  );

  const exportCsv = async () => {
    setExporting(true);
    try {
      const statuses = statusFilter === 'all' ? HISTORY_STATUSES : [statusFilter];
      const collected: SuggestItem[] = [];
      let truncated = false;
      for (const status of statuses) {
        const page = await listAllSuggestItems({ settingsId, status, limit: 100 });
        collected.push(
          ...page.items.filter((item) =>
            suggestItemInScope(item, { workspaceId, settingsId, channelId }),
          ),
        );
        truncated = truncated || page.truncated;
      }
      const blob = new Blob([toCsv(collected)], { type: 'text/csv;charset=utf-8' });
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = `suggest-history-${settingsId}.csv`;
      anchor.click();
      URL.revokeObjectURL(url);
      if (truncated) {
        toast({ title: t('exportTruncated') });
      }
    } catch (error) {
      toast({
        variant: 'destructive',
        title: t('loadErrorTitle'),
        description: getErrorMessage(error),
      });
    } finally {
      setExporting(false);
    }
  };

  return (
    <section className="space-y-4" data-testid="suggest-history" aria-label={t('title')}>
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h2 className="text-lg font-semibold">{t('title')}</h2>
          <p className="text-sm text-muted-foreground">{t('description')}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Select
            value={statusFilter}
            onValueChange={(value) => setStatusFilter(value as ItemStatus | 'all')}
          >
            <SelectTrigger className="w-[200px]" aria-label={t('statusFilter')}>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {HISTORY_FILTERS.map((value) => (
                <SelectItem key={value} value={value}>
                  {t(`filters.${value}`)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Button
            size="sm"
            variant="outline"
            onClick={() => void exportCsv()}
            disabled={exporting || (items.length === 0 && !itemsQuery.hasNextPage)}
            data-testid="suggest-history-export-csv"
          >
            {t('exportCsv')}
          </Button>
        </div>
      </div>

      {itemsQuery.isLoading ? (
        <Skeleton className="h-32 w-full" />
      ) : itemsQuery.isError ? (
        <EmptyState
          role="alert"
          title={t('loadErrorTitle')}
          description={getErrorMessage(itemsQuery.error)}
          action={
            <Button type="button" variant="outline" onClick={() => itemsQuery.refetch()}>
              {t('retry')}
            </Button>
          }
        />
      ) : items.length === 0 && !itemsQuery.hasNextPage ? (
        <EmptyState
          icon={<History className="h-6 w-6" aria-hidden />}
          title={t('emptyTitle')}
          description={t('emptyBody')}
        />
      ) : (
        <ul className="divide-y divide-border">
          {items.map((item) => (
            <li key={item.id} className="space-y-1 py-3" data-testid={`suggest-history-${item.id}`}>
              <div className="flex flex-wrap items-center gap-2 text-sm">
                <span className="font-medium">{t(`filters.${item.status as ItemStatus}`)}</span>
                <span className="text-muted-foreground">
                  {item.authorVkId ? `id${item.authorVkId}` : '-'}
                </span>
                {item.decidedAt || item.createdAt ? (
                  <time className="text-xs text-muted-foreground">
                    {new Date(item.decidedAt ?? item.createdAt!).toLocaleString('ru-RU')}
                  </time>
                ) : null}
              </div>
              <p className="text-sm text-foreground">{(item.text ?? '').slice(0, 200)}</p>
              <ReasonChips codes={item.reasons ?? []} />
            </li>
          ))}
          {itemsQuery.hasNextPage ? (
            <li className="pt-3">
              <Button
                type="button"
                variant="outline"
                size="sm"
                disabled={itemsQuery.isFetchingNextPage}
                onClick={() => itemsQuery.fetchNextPage()}
                data-testid="suggest-history-load-more"
              >
                {t('loadMore')}
              </Button>
            </li>
          ) : null}
        </ul>
      )}
    </section>
  );
}
