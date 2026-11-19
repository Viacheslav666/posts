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
} from '@posts/ui';
import { useQuery } from '@tanstack/react-query';
import { History } from 'lucide-react';
import { useMemo, useState } from 'react';
import { useTranslations } from 'next-intl';

import { ReasonChips } from '@/features/automations-common';
import { queryKeys } from '@/shared/api-client';

import { listSuggestItems } from '../api/suggest-api';
import { HISTORY_FILTERS, type ItemStatus } from '../model/types';

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

export function HistoryPanel({ workspaceId, settings }: HistoryPanelProps) {
  const t = useTranslations('automations.suggest.history');
  const settingsId = settings.id!;
  const [statusFilter, setStatusFilter] = useState<ItemStatus | 'all'>('published');

  const itemsQuery = useQuery({
    queryKey: queryKeys.suggest.items(workspaceId, settingsId, {
      history: true,
      status: statusFilter === 'all' ? undefined : statusFilter,
    }),
    queryFn: async () => {
      if (statusFilter === 'all') {
        const [published, rejected, deleted] = await Promise.all([
          listSuggestItems({ settingsId, status: 'published' }),
          listSuggestItems({ settingsId, status: 'rejected' }),
          listSuggestItems({ settingsId, status: 'deleted_remote' }),
        ]);
        return {
          items: [
            ...(published.items ?? []),
            ...(rejected.items ?? []),
            ...(deleted.items ?? []),
          ],
          hasMore: false,
        };
      }
      return listSuggestItems({ settingsId, status: statusFilter });
    },
  });

  const items = useMemo(() => itemsQuery.data?.items ?? [], [itemsQuery.data?.items]);

  const exportCsv = () => {
    const blob = new Blob([toCsv(items)], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = `suggest-history-${settingsId}.csv`;
    anchor.click();
    URL.revokeObjectURL(url);
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
            onClick={exportCsv}
            disabled={items.length === 0}
            data-testid="suggest-history-export-csv"
          >
            {t('exportCsv')}
          </Button>
        </div>
      </div>

      {itemsQuery.isLoading ? (
        <Skeleton className="h-32 w-full" />
      ) : items.length === 0 ? (
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
                  {item.authorVkId ? `id${item.authorVkId}` : '—'}
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
        </ul>
      )}
    </section>
  );
}
