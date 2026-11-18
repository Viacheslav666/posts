'use client';

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
import { BarChart3, RefreshCw, ShieldOff } from 'lucide-react';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { useCallback, useMemo } from 'react';

import { useCan } from '@/shared/hooks/use-can';
import { getErrorMessage } from '@/shared/lib/errors';

import {
  usePostsAnalytics,
  useRetryPublicationMutation,
} from '../hooks/use-analytics-queries';
import {
  aggregateTopErrors,
  isAnalyticsYearEmpty,
  toNetworkOutcomeSeries,
  toVolumeSeries,
} from '../model/aggregate';
import { analyticsYearOptions, parseAnalyticsYear } from '../model/year';
import { NetworksOutcomeChart } from './networks-outcome-chart';
import { OrdRevenueTable } from './ord-revenue-table';
import { PostsVolumeChart } from './posts-volume-chart';
import { TopErrorsTable } from './top-errors-table';

type AnalyticsPageProps = {
  workspaceId: string;
};

export function AnalyticsPage({ workspaceId }: AnalyticsPageProps) {
  const t = useTranslations('analytics');
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const canView = useCan('analytics.view', workspaceId);
  const canRetry = useCan('posts.retry_publish', workspaceId);

  const year = parseAnalyticsYear(searchParams.get('year'));
  const yearOptions = useMemo(() => analyticsYearOptions(), []);

  const analyticsQuery = usePostsAnalytics(workspaceId, year, canView);
  const retryMutation = useRetryPublicationMutation(workspaceId);

  const setYear = useCallback(
    (nextYear: number) => {
      const params = new URLSearchParams(searchParams.toString());
      params.set('year', String(nextYear));
      router.replace(`${pathname}?${params.toString()}`, { scroll: false });
    },
    [pathname, router, searchParams],
  );

  const volume = useMemo(
    () => toVolumeSeries(analyticsQuery.data?.months ?? []),
    [analyticsQuery.data?.months],
  );
  const networkOutcomes = useMemo(
    () => toNetworkOutcomeSeries(analyticsQuery.data?.months ?? []),
    [analyticsQuery.data?.months],
  );
  const topErrors = useMemo(
    () => aggregateTopErrors(analyticsQuery.data?.months ?? []),
    [analyticsQuery.data?.months],
  );
  const isEmpty = isAnalyticsYearEmpty(analyticsQuery.data);

  const handleRetry = useCallback(
    (publicationId: string) => {
      retryMutation.mutate(publicationId, {
        onSuccess: () => {
          toast({ title: t('errors.retrySuccess') });
        },
        onError: (error) => {
          toast({
            variant: 'destructive',
            title: t('errors.retryError'),
            description: getErrorMessage(error, t('errors.retryError')),
          });
        },
      });
    },
    [retryMutation, t],
  );

  if (!canView) {
    return (
      <EmptyState
        icon={<ShieldOff aria-hidden />}
        title={t('forbiddenTitle')}
        description={t('forbiddenBody')}
        data-testid="analytics-forbidden"
      />
    );
  }

  return (
    <div className="mx-auto flex w-full max-w-5xl flex-col gap-6" data-testid="analytics-page">
      <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div className="space-y-2">
          <h1 className="font-display text-3xl font-semibold tracking-tight text-foreground">
            {t('title')}
          </h1>
          <p className="max-w-2xl text-sm text-muted-foreground">{t('description')}</p>
        </div>

        <div className="flex items-center gap-2" data-testid="analytics-year-select">
          <label htmlFor="analytics-year" className="text-sm text-muted-foreground">
            {t('yearLabel')}
          </label>
          <Select value={String(year)} onValueChange={(value) => setYear(Number(value))}>
            <SelectTrigger id="analytics-year" className="w-[7.5rem]" aria-label={t('yearLabel')}>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {yearOptions.map((option) => (
                <SelectItem key={option} value={String(option)}>
                  {option}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </header>

      {analyticsQuery.isLoading ? <AnalyticsSkeleton /> : null}

      {analyticsQuery.isError ? (
        <EmptyState
          icon={<RefreshCw aria-hidden />}
          title={t('errorTitle')}
          description={getErrorMessage(analyticsQuery.error, t('errorBody'))}
          action={
            <Button
              type="button"
              variant="secondary"
              onClick={() => void analyticsQuery.refetch()}
              data-testid="analytics-error-retry-load"
            >
              {t('errorRetry')}
            </Button>
          }
          data-testid="analytics-error"
        />
      ) : null}

      {!analyticsQuery.isLoading && !analyticsQuery.isError && isEmpty ? (
        <EmptyState
          icon={<BarChart3 aria-hidden />}
          title={t('emptyTitle')}
          description={t('emptyBody')}
          data-testid="analytics-empty"
        />
      ) : null}

      {!analyticsQuery.isLoading && !analyticsQuery.isError && !isEmpty && analyticsQuery.data ? (
        <>
          <PostsVolumeChart data={volume} />
          <NetworksOutcomeChart data={networkOutcomes} />
          <OrdRevenueTable months={analyticsQuery.data.months ?? []} />
          <TopErrorsTable
            workspaceId={workspaceId}
            errors={topErrors}
            canRetry={canRetry}
            retryingId={retryMutation.isPending ? retryMutation.variables : null}
            onRetry={handleRetry}
          />
          {analyticsQuery.data.timezone ? (
            <p className="text-xs text-muted-foreground" data-testid="analytics-timezone">
              {t('timezone', { zone: analyticsQuery.data.timezone })}
            </p>
          ) : null}
        </>
      ) : null}
    </div>
  );
}

function AnalyticsSkeleton() {
  return (
    <div className="space-y-4" data-testid="analytics-loading">
      <Skeleton className="h-72 w-full rounded-xl" />
      <Skeleton className="h-80 w-full rounded-xl" />
      <Skeleton className="h-64 w-full rounded-xl" />
      <Skeleton className="h-48 w-full rounded-xl" />
    </div>
  );
}
