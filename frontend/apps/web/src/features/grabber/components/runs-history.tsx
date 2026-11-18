'use client';

import { EmptyState, Skeleton } from '@posts/ui';
import { useQuery } from '@tanstack/react-query';
import { History } from 'lucide-react';
import { useTranslations } from 'next-intl';

import { queryKeys } from '@/shared/api-client';

import { listIngestRuns } from '../api/grabber-api';

type RunsHistoryProps = {
  workspaceId: string;
  ruleId: string;
};

export function RunsHistory({ workspaceId, ruleId }: RunsHistoryProps) {
  const t = useTranslations('grabber.runs');
  const query = useQuery({
    queryKey: queryKeys.ingest.runs(workspaceId, ruleId),
    queryFn: () => listIngestRuns(ruleId),
    enabled: Boolean(ruleId),
  });

  if (query.isLoading) {
    return <Skeleton className="h-32 w-full" />;
  }

  const items = query.data?.items ?? [];
  if (!items.length) {
    return (
      <EmptyState
        icon={<History className="h-6 w-6" aria-hidden />}
        title={t('emptyTitle')}
        description={t('emptyBody')}
      />
    );
  }

  return (
    <div className="overflow-x-auto" data-testid="grabber-runs-history">
      <table className="w-full min-w-[640px] text-left text-sm">
        <thead className="border-b border-border text-muted-foreground">
          <tr>
            <th className="px-2 py-2 font-medium">{t('started')}</th>
            <th className="px-2 py-2 font-medium">{t('trigger')}</th>
            <th className="px-2 py-2 font-medium">{t('status')}</th>
            <th className="px-2 py-2 font-medium">{t('new')}</th>
            <th className="px-2 py-2 font-medium">{t('deduped')}</th>
          </tr>
        </thead>
        <tbody>
          {items.map((run) => (
            <tr key={run.id} className="border-b border-border/70">
              <td className="px-2 py-2">
                {run.startedAt ? new Date(run.startedAt).toLocaleString('ru-RU') : '—'}
              </td>
              <td className="px-2 py-2">{run.trigger ?? '—'}</td>
              <td className="px-2 py-2">{run.status ?? '—'}</td>
              <td className="px-2 py-2">{run.itemsNew ?? 0}</td>
              <td className="px-2 py-2">{run.itemsDeduped ?? 0}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
