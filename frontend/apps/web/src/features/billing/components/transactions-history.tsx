'use client';

import { Button, EmptyState, Select, SelectContent, SelectItem, SelectTrigger, SelectValue, Skeleton } from '@posts/ui';
import { useTranslations } from 'next-intl';
import { useMemo, useState } from 'react';

import { useTransactions } from '../hooks/use-billing-queries';
import { formatKopecks } from '../model/money';
import { isDownloadableReceiptUrl, TRANSACTION_KINDS } from '../model/transaction-kinds';

type TransactionsHistoryProps = {
  workspaceId: string;
};

function formatDate(iso?: string): string {
  if (!iso) {
    return '—';
  }
  try {
    return new Intl.DateTimeFormat('ru-RU', {
      dateStyle: 'short',
      timeStyle: 'short',
    }).format(new Date(iso));
  } catch {
    return iso;
  }
}

export function TransactionsHistory({ workspaceId }: TransactionsHistoryProps) {
  const t = useTranslations('billing.transactions');
  const [kind, setKind] = useState<string>('all');
  const [cursor, setCursor] = useState<string | undefined>(undefined);
  const [loaded, setLoaded] = useState<NonNullable<ReturnType<typeof useTransactions>['data']>['items']>([]);

  const query = useTransactions(workspaceId, {
    kind: kind === 'all' ? undefined : kind,
    cursor,
    limit: 10,
  });

  const items = useMemo(() => {
    const pageItems = query.data?.items ?? [];
    if (!cursor) {
      return pageItems;
    }
    const seen = new Set((loaded ?? []).map((item) => item.id));
    const merged = [...(loaded ?? [])];
    for (const item of pageItems) {
      if (item.id && !seen.has(item.id)) {
        merged.push(item);
      }
    }
    return merged;
  }, [cursor, loaded, query.data?.items]);

  return (
    <section className="space-y-4 rounded-xl border border-border p-4" data-testid="billing-transactions">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 className="text-lg font-semibold text-foreground">{t('title')}</h2>
          <p className="text-sm text-muted-foreground">{t('description')}</p>
        </div>
        <label className="space-y-1 text-sm">
          <span className="sr-only">{t('filterLabel')}</span>
          <Select
            value={kind}
            onValueChange={(value) => {
              setKind(value);
              setCursor(undefined);
              setLoaded([]);
            }}
          >
            <SelectTrigger className="w-[200px]" data-testid="billing-tx-filter">
              <SelectValue placeholder={t('filterAll')} />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">{t('filterAll')}</SelectItem>
              {TRANSACTION_KINDS.map((value) => (
                <SelectItem key={value} value={value}>
                  {t(`kinds.${value}`)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </label>
      </div>

      {query.isLoading && !cursor ? (
        <div className="space-y-2" data-testid="billing-tx-loading">
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-10 w-full" />
        </div>
      ) : null}

      {!query.isLoading && items.length === 0 ? (
        <EmptyState title={t('emptyTitle')} description={t('emptyBody')} data-testid="billing-tx-empty" />
      ) : null}

      {items.length > 0 ? (
        <div
          className="overflow-x-auto"
          tabIndex={0}
          role="region"
          aria-label={t('title')}
        >
          <table className="w-full min-w-[640px] text-left text-sm" data-testid="billing-tx-table">
            <thead className="border-b border-border text-muted-foreground">
              <tr>
                <th className="px-2 py-2 font-medium">{t('columns.date')}</th>
                <th className="px-2 py-2 font-medium">{t('columns.kind')}</th>
                <th className="px-2 py-2 font-medium">{t('columns.amount')}</th>
                <th className="px-2 py-2 font-medium">{t('columns.method')}</th>
                <th className="px-2 py-2 font-medium">{t('columns.receipt')}</th>
                <th className="px-2 py-2 font-medium">{t('columns.details')}</th>
              </tr>
            </thead>
            <tbody>
              {items.map((tx) => (
                <tr key={tx.id} className="border-b border-border/60" data-testid={`billing-tx-row-${tx.id}`}>
                  <td className="px-2 py-2 whitespace-nowrap">{formatDate(tx.createdAt)}</td>
                  <td className="px-2 py-2">
                    {tx.kind && TRANSACTION_KINDS.includes(tx.kind as (typeof TRANSACTION_KINDS)[number])
                      ? t(`kinds.${tx.kind as (typeof TRANSACTION_KINDS)[number]}`)
                      : (tx.kind ?? '—')}
                  </td>
                  <td
                    className={`px-2 py-2 font-medium ${(tx.amountKopecks ?? 0) < 0 ? 'text-destructive' : 'text-foreground'}`}
                  >
                    {formatKopecks(tx.amountKopecks ?? 0, { signed: true })}
                    {tx.bonus ? (
                      <span className="ml-1 text-xs text-muted-foreground">{t('bonusTag')}</span>
                    ) : null}
                  </td>
                  <td className="px-2 py-2">{tx.method ?? '—'}</td>
                  <td className="px-2 py-2">
                    {isDownloadableReceiptUrl(tx.receiptUrl) ? (
                      <a
                        href={tx.receiptUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="text-primary underline-offset-2 hover:underline"
                      >
                        {t('receiptLink')}
                      </a>
                    ) : tx.receiptUrl ? (
                      <span className="text-muted-foreground" title={t('receiptUnavailableHint')}>
                        {t('receiptIdOnly')}
                      </span>
                    ) : (
                      '—'
                    )}
                  </td>
                  <td className="px-2 py-2 text-muted-foreground">{tx.details ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}

      {query.data?.hasMore ? (
        <Button
          type="button"
          variant="outline"
          size="sm"
          data-testid="billing-tx-more"
          disabled={query.isFetching}
          onClick={() => {
            setLoaded(items);
            setCursor(query.data?.nextCursor);
          }}
        >
          {query.isFetching ? t('loadingMore') : t('loadMore')}
        </Button>
      ) : null}
    </section>
  );
}
