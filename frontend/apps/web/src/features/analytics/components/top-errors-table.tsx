'use client';

import { Button, EmptyState } from '@posts/ui';
import { AlertTriangle, ExternalLink, RotateCcw } from 'lucide-react';
import Link from 'next/link';
import { useTranslations } from 'next-intl';

import type { AggregatedTopError } from '../model/aggregate';
import { postsListHrefForError, resolveRetryPublicationId } from '../model/aggregate';

type TopErrorsTableProps = {
  workspaceId: string;
  errors: AggregatedTopError[];
  canRetry: boolean;
  retryingId?: string | null;
  onRetry: (publicationId: string) => void;
};

export function TopErrorsTable({
  workspaceId,
  errors,
  canRetry,
  retryingId,
  onRetry,
}: TopErrorsTableProps) {
  const t = useTranslations('analytics.errors');
  const tn = useTranslations('analytics.networks');

  return (
    <section
      className="space-y-3 rounded-xl border border-border bg-card/40 p-4"
      aria-labelledby="analytics-errors-title"
      data-testid="analytics-errors"
    >
      <div>
        <h2 id="analytics-errors-title" className="text-lg font-semibold text-foreground">
          {t('title')}
        </h2>
        <p className="text-sm text-muted-foreground">{t('description')}</p>
      </div>

      {errors.length === 0 ? (
        <EmptyState
          icon={<AlertTriangle aria-hidden />}
          title={t('emptyTitle')}
          description={t('emptyBody')}
          data-testid="analytics-errors-empty"
        />
      ) : (
        <div
          className="overflow-x-auto"
          role="region"
          aria-label={t('title')}
          tabIndex={0}
        >
          <table className="w-full min-w-[40rem] border-collapse text-sm" data-testid="analytics-errors-table">
            <thead>
              <tr className="border-b border-border text-left text-muted-foreground">
                <th className="px-2 py-2 font-medium">{t('code')}</th>
                <th className="px-2 py-2 font-medium">{t('message')}</th>
                <th className="px-2 py-2 font-medium">{t('network')}</th>
                <th className="px-2 py-2 font-medium">{t('count')}</th>
                <th className="px-2 py-2 font-medium">{t('actions')}</th>
              </tr>
            </thead>
            <tbody>
              {errors.map((error) => {
                const publicationId = resolveRetryPublicationId(error);
                const href = postsListHrefForError(workspaceId, error);
                return (
                  <tr
                    key={error.key}
                    className="border-b border-border/60"
                    data-testid={`analytics-error-row-${error.errorCode}`}
                  >
                    <td className="px-2 py-2 font-mono text-xs">{error.errorCode}</td>
                    <td className="px-2 py-2">{error.errorMessage || t('noMessage')}</td>
                    <td className="px-2 py-2">
                      {error.network && ['vk', 'ok', 'tg', 'max'].includes(error.network)
                        ? tn(error.network as 'vk' | 'ok' | 'tg' | 'max')
                        : (error.network ?? '—')}
                    </td>
                    <td className="px-2 py-2 tabular-nums">{error.count}</td>
                    <td className="px-2 py-2">
                      <div className="flex flex-wrap items-center gap-2">
                        <Button asChild size="sm" variant="outline">
                          <Link href={href} data-testid="analytics-error-open">
                            <ExternalLink aria-hidden className="size-3.5" />
                            {error.postId ? t('openPost') : t('openList')}
                          </Link>
                        </Button>
                        {canRetry && publicationId ? (
                          <Button
                            type="button"
                            size="sm"
                            variant="secondary"
                            disabled={retryingId === publicationId}
                            onClick={() => onRetry(publicationId)}
                            data-testid="analytics-error-retry"
                          >
                            <RotateCcw aria-hidden className="size-3.5" />
                            {t('retry')}
                          </Button>
                        ) : null}
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
