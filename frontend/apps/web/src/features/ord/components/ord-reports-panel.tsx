'use client';

import type { OrdEridCheckResponse, OrdProvider, OrdReportRow } from '@posts/api-client';
import {
  Button,
  ConfirmActionDialog,
  EmptyState,
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Skeleton,
  toast,
} from '@posts/ui';
import { useTranslations } from 'next-intl';
import { useEffect, useMemo, useState } from 'react';

import { useCan } from '@/shared/hooks/use-can';
import { getErrorMessage, isApiProblemError } from '@/shared/lib/errors';

import {
  useCheckOrdErid,
  useDeleteOrdInvoice,
  useExportOrdReports,
  useOrdReports,
  useRunOrdReport,
} from '../hooks/use-ord-queries';
import { ORD_PROVIDERS } from '../model/schemas';
import type { OrdExportFormat } from '../model/report-export';
import { ordExportQueryFromFilters } from '../model/report-export';
import {
  defaultOrdReportFilters,
  ORD_REPORT_PAGE_SIZE,
  ORD_REPORT_STATUSES,
  ordReportFiltersToQuery,
  paginateItems,
  yearOptions,
  type OrdReportFiltersState,
  type OrdReportStatus,
} from '../model/report-filters';
import {
  mapErirErrorMessage,
  ORD_PROBLEM_CODE_LABELS_RU,
  ordEridStatusLabelRu,
} from '../model/report-status-labels';
import { OrdReportStatusBadge } from './ord-report-status-badge';

type OrdReportsPanelProps = {
  workspaceId: string;
};

function triggerDownload(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  anchor.rel = 'noopener';
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}

function formatAmount(amount?: number): string {
  if (amount == null || Number.isNaN(amount)) return '—';
  return new Intl.NumberFormat('ru-RU', {
    style: 'currency',
    currency: 'RUB',
    maximumFractionDigits: 2,
  }).format(amount);
}

function formatPeriod(year?: number, month?: number): string {
  if (!year || !month) return '—';
  return `${String(month).padStart(2, '0')}.${year}`;
}

function providerLabel(provider: OrdProvider | undefined, labels: Record<string, string>): string {
  if (!provider) return '—';
  return labels[provider] ?? provider;
}

export function OrdReportsPanel({ workspaceId }: OrdReportsPanelProps) {
  const t = useTranslations('ord');
  const tReports = useTranslations('ord.reports');
  const canRun = useCan('ord.reports.run', workspaceId);
  const providerLabels = {
    vk_ord: t('providers.vk_ord'),
    yandex_ord: t('providers.yandex_ord'),
  };

  const [filters, setFilters] = useState<OrdReportFiltersState>(() => defaultOrdReportFilters());
  const [page, setPage] = useState(1);
  const [eridInput, setEridInput] = useState('');
  const [eridResult, setEridResult] = useState<OrdEridCheckResponse | null>(null);
  const [pendingDelete, setPendingDelete] = useState<OrdReportRow | null>(null);

  const apiQuery = useMemo(() => ordReportFiltersToQuery(filters), [filters]);
  const query = useOrdReports(workspaceId, apiQuery);
  const runReport = useRunOrdReport(workspaceId);
  const checkErid = useCheckOrdErid();
  const deleteInvoice = useDeleteOrdInvoice(workspaceId);
  const exportReports = useExportOrdReports(workspaceId);

  const allItems = query.data?.items ?? [];
  const slice = paginateItems(allItems, page, ORD_REPORT_PAGE_SIZE);

  useEffect(() => {
    setPage(1);
  }, [apiQuery.year, apiQuery.month, apiQuery.provider, apiQuery.status, apiQuery.erid]);

  useEffect(() => {
    if (page !== slice.page) {
      setPage(slice.page);
    }
  }, [page, slice.page]);

  const years = useMemo(() => yearOptions(defaultOrdReportFilters().year ?? 2026), []);

  function problemDescription(error: unknown): string {
    if (isApiProblemError(error) && ORD_PROBLEM_CODE_LABELS_RU[error.code]) {
      return ORD_PROBLEM_CODE_LABELS_RU[error.code];
    }
    return getErrorMessage(error);
  }

  function updateFilter<K extends keyof OrdReportFiltersState>(
    key: K,
    value: OrdReportFiltersState[K],
  ) {
    setFilters((prev) => ({ ...prev, [key]: value }));
  }

  function handleRun() {
    if (!filters.year || !filters.month) {
      toast({
        variant: 'destructive',
        title: tReports('runErrorTitle'),
        description: tReports('periodRequired'),
      });
      return;
    }
    runReport.mutate(
      {
        workspaceId,
        year: filters.year,
        month: filters.month,
        provider: filters.provider || undefined,
      },
      {
        onSuccess: (result) => {
          toast({
            title: tReports('runSuccessTitle'),
            description: tReports('runSuccessBody', {
              invoices: result.invoicesCount ?? 0,
              statistics: result.statisticsCount ?? 0,
            }),
          });
        },
        onError: (error) => {
          toast({
            variant: 'destructive',
            title: tReports('runErrorTitle'),
            description: problemDescription(error),
          });
        },
      },
    );
  }

  function handleCheckErid() {
    const erid = eridInput.trim();
    if (!erid) {
      toast({
        variant: 'destructive',
        title: tReports('eridErrorTitle'),
        description: tReports('eridRequired'),
      });
      return;
    }
    checkErid.mutate(
      {
        workspaceId,
        erid,
        provider: filters.provider || undefined,
      },
      {
        onSuccess: (result) => {
          setEridResult(result);
          toast({
            title: result.found ? tReports('eridFoundTitle') : tReports('eridNotFoundTitle'),
            description: ordEridStatusLabelRu(result.status),
          });
        },
        onError: (error) => {
          setEridResult(null);
          toast({
            variant: 'destructive',
            title: tReports('eridErrorTitle'),
            description: problemDescription(error),
          });
        },
      },
    );
  }

  function handleExport(format: OrdExportFormat) {
    exportReports.mutate(
      { ...ordExportQueryFromFilters(apiQuery, format), format },
      {
        onSuccess: ({ blob, filename }) => {
          triggerDownload(blob, filename);
          toast({ title: tReports('exportSuccess') });
        },
        onError: (error) => {
          toast({
            variant: 'destructive',
            title: tReports('exportErrorTitle'),
            description: problemDescription(error),
          });
        },
      },
    );
  }

  return (
    <section className="space-y-4" data-testid="ord-reports-panel">
      <div className="flex flex-col gap-1 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h2 className="text-lg font-semibold text-foreground">{tReports('title')}</h2>
          <p className="text-sm text-muted-foreground">{tReports('description')}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          {canRun ? (
            <Button
              type="button"
              size="sm"
              data-testid="ord-reports-run"
              disabled={runReport.isPending}
              onClick={handleRun}
            >
              {runReport.isPending ? tReports('running') : tReports('run')}
            </Button>
          ) : null}
          <Button
            type="button"
            size="sm"
            variant="outline"
            data-testid="ord-reports-export-csv"
            disabled={exportReports.isPending}
            onClick={() => handleExport('csv')}
          >
            {tReports('exportCsv')}
          </Button>
          <Button
            type="button"
            size="sm"
            variant="outline"
            data-testid="ord-reports-export-xlsx"
            disabled={exportReports.isPending}
            onClick={() => handleExport('xlsx')}
          >
            {tReports('exportXlsx')}
          </Button>
        </div>
      </div>

      <div
        className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5"
        data-testid="ord-reports-filters"
        role="search"
        aria-label={tReports('filtersLabel')}
      >
        <div className="space-y-1.5">
          <label htmlFor="ord-reports-year" className="text-sm font-medium text-foreground">
            {tReports('filters.year')}
          </label>
          <Select
            value={filters.year != null ? String(filters.year) : undefined}
            onValueChange={(value) => updateFilter('year', Number(value))}
          >
            <SelectTrigger id="ord-reports-year" data-testid="ord-reports-filter-year">
              <SelectValue placeholder={tReports('filters.year')} />
            </SelectTrigger>
            <SelectContent>
              {years.map((year) => (
                <SelectItem key={year} value={String(year)}>
                  {year}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div className="space-y-1.5">
          <label htmlFor="ord-reports-month" className="text-sm font-medium text-foreground">
            {tReports('filters.month')}
          </label>
          <Select
            value={filters.month != null ? String(filters.month) : undefined}
            onValueChange={(value) => updateFilter('month', Number(value))}
          >
            <SelectTrigger id="ord-reports-month" data-testid="ord-reports-filter-month">
              <SelectValue placeholder={tReports('filters.month')} />
            </SelectTrigger>
            <SelectContent>
              {Array.from({ length: 12 }, (_, index) => index + 1).map((month) => (
                <SelectItem key={month} value={String(month)}>
                  {tReports(`months.${month}`)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div className="space-y-1.5">
          <label htmlFor="ord-reports-provider" className="text-sm font-medium text-foreground">
            {tReports('filters.provider')}
          </label>
          <Select
            value={filters.provider || 'all'}
            onValueChange={(value) =>
              updateFilter('provider', value === 'all' ? '' : (value as OrdProvider))
            }
          >
            <SelectTrigger id="ord-reports-provider" data-testid="ord-reports-filter-provider">
              <SelectValue placeholder={tReports('filters.providerAll')} />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">{tReports('filters.providerAll')}</SelectItem>
              {ORD_PROVIDERS.map((provider) => (
                <SelectItem key={provider} value={provider}>
                  {t(`providers.${provider}`)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div className="space-y-1.5">
          <label htmlFor="ord-reports-status" className="text-sm font-medium text-foreground">
            {tReports('filters.status')}
          </label>
          <Select
            value={filters.status || 'all'}
            onValueChange={(value) =>
              updateFilter('status', value === 'all' ? '' : (value as OrdReportStatus))
            }
          >
            <SelectTrigger id="ord-reports-status" data-testid="ord-reports-filter-status">
              <SelectValue placeholder={tReports('filters.statusAll')} />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">{tReports('filters.statusAll')}</SelectItem>
              {ORD_REPORT_STATUSES.map((status) => (
                <SelectItem key={status} value={status}>
                  {tReports(`status.${status}`)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div className="space-y-1.5">
          <label htmlFor="ord-reports-search" className="text-sm font-medium text-foreground">
            {tReports('filters.search')}
          </label>
          <Input
            id="ord-reports-search"
            data-testid="ord-reports-filter-erid"
            value={filters.erid ?? ''}
            placeholder={tReports('filters.searchPlaceholder')}
            onChange={(event) => updateFilter('erid', event.target.value)}
          />
        </div>
      </div>

      <div
        className="flex flex-col gap-2 rounded-lg border border-border p-3 sm:flex-row sm:items-end"
        data-testid="ord-reports-erid-check"
      >
        <div className="min-w-0 flex-1 space-y-1.5">
          <label htmlFor="ord-reports-erid-input" className="text-sm font-medium text-foreground">
            {tReports('eridLabel')}
          </label>
          <Input
            id="ord-reports-erid-input"
            data-testid="ord-reports-erid-input"
            value={eridInput}
            placeholder={tReports('eridPlaceholder')}
            onChange={(event) => setEridInput(event.target.value)}
          />
        </div>
        <Button
          type="button"
          size="sm"
          variant="secondary"
          data-testid="ord-reports-erid-submit"
          disabled={checkErid.isPending}
          onClick={handleCheckErid}
        >
          {checkErid.isPending ? tReports('eridChecking') : tReports('eridCheck')}
        </Button>
      </div>

      {eridResult ? (
        <p
          className="text-sm text-muted-foreground"
          data-testid="ord-reports-erid-result"
          role="status"
        >
          {tReports('eridResult', {
            erid: eridResult.erid ?? eridInput,
            status: ordEridStatusLabelRu(eridResult.status),
            found: eridResult.found ? tReports('eridYes') : tReports('eridNo'),
          })}
        </p>
      ) : null}

      {query.isLoading ? (
        <div className="space-y-2" data-testid="ord-reports-loading">
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-10 w-full" />
        </div>
      ) : null}

      {!query.isLoading && allItems.length === 0 ? (
        <EmptyState
          title={tReports('emptyTitle')}
          description={tReports('emptyBody')}
          data-testid="ord-reports-empty"
        />
      ) : null}

      {slice.items.length > 0 ? (
        <div
          className="overflow-x-auto"
          tabIndex={0}
          role="region"
          aria-label={tReports('tableLabel')}
        >
          <table
            className="w-full min-w-[720px] text-left text-sm"
            data-testid="ord-reports-table"
          >
            <thead className="border-b border-border text-muted-foreground">
              <tr>
                <th className="px-2 py-2 font-medium">{tReports('columns.period')}</th>
                <th className="px-2 py-2 font-medium">{tReports('columns.provider')}</th>
                <th className="px-2 py-2 font-medium">{tReports('columns.erid')}</th>
                <th className="px-2 py-2 font-medium">{tReports('columns.platform')}</th>
                <th className="px-2 py-2 font-medium">{tReports('columns.impressions')}</th>
                <th className="px-2 py-2 font-medium">{tReports('columns.amount')}</th>
                <th className="px-2 py-2 font-medium">{tReports('columns.status')}</th>
                <th className="px-2 py-2 font-medium">{tReports('columns.error')}</th>
                <th className="px-2 py-2 font-medium">{tReports('columns.actions')}</th>
              </tr>
            </thead>
            <tbody>
              {slice.items.map((row) => {
                const rowKey =
                  row.statisticsId ??
                  row.invoiceId ??
                  `${row.erid}-${row.periodYear}-${row.periodMonth}-${row.provider}`;
                const erirError = mapErirErrorMessage(row.error);
                return (
                  <tr
                    key={rowKey}
                    className="border-b border-border/60"
                    data-testid={`ord-report-row-${row.invoiceId ?? row.statisticsId}`}
                  >
                    <td className="px-2 py-2">{formatPeriod(row.periodYear, row.periodMonth)}</td>
                    <td className="px-2 py-2">{providerLabel(row.provider, providerLabels)}</td>
                    <td className="px-2 py-2 font-mono text-xs">{row.erid ?? '—'}</td>
                    <td className="px-2 py-2">{row.platformName ?? '—'}</td>
                    <td className="px-2 py-2">
                      {row.impressions != null
                        ? new Intl.NumberFormat('ru-RU').format(row.impressions)
                        : '—'}
                    </td>
                    <td className="px-2 py-2">{formatAmount(row.amount)}</td>
                    <td className="px-2 py-2">
                      <OrdReportStatusBadge status={row.status} />
                    </td>
                    <td className="max-w-[220px] px-2 py-2 text-xs text-muted-foreground">
                      {erirError ?? '—'}
                    </td>
                    <td className="px-2 py-2">
                      {canRun && row.invoiceId ? (
                        <Button
                          type="button"
                          size="sm"
                          variant="outline"
                          data-testid={`ord-report-delete-${row.invoiceId}`}
                          onClick={() => setPendingDelete(row)}
                        >
                          {tReports('delete')}
                        </Button>
                      ) : (
                        <span className="text-muted-foreground">—</span>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      ) : null}

      {allItems.length > 0 ? (
        <div
          className="flex flex-wrap items-center justify-between gap-2"
          data-testid="ord-reports-pagination"
        >
          <p className="text-sm text-muted-foreground">
            {tReports('pagination', {
              page: slice.page,
              totalPages: slice.totalPages,
              total: slice.total,
            })}
          </p>
          <div className="flex gap-2">
            <Button
              type="button"
              size="sm"
              variant="outline"
              data-testid="ord-reports-prev"
              disabled={!slice.hasPrev}
              onClick={() => setPage((prev) => Math.max(1, prev - 1))}
            >
              {tReports('prev')}
            </Button>
            <Button
              type="button"
              size="sm"
              variant="outline"
              data-testid="ord-reports-next"
              disabled={!slice.hasNext}
              onClick={() => setPage((prev) => prev + 1)}
            >
              {tReports('next')}
            </Button>
          </div>
        </div>
      ) : null}

      <ConfirmActionDialog
        open={Boolean(pendingDelete)}
        onOpenChange={(open) => !open && setPendingDelete(null)}
        title={tReports('confirmDeleteTitle')}
        description={tReports('confirmDeleteBody', {
          erid: pendingDelete?.erid ?? '',
          period: formatPeriod(pendingDelete?.periodYear, pendingDelete?.periodMonth),
        })}
        confirmLabel={tReports('delete')}
        cancelLabel={tReports('cancel')}
        busy={deleteInvoice.isPending}
        testId="ord-report-delete-dialog"
        confirmTestId="ord-report-delete-confirm"
        onConfirm={() => {
          if (!pendingDelete?.invoiceId) return;
          deleteInvoice.mutate(pendingDelete.invoiceId, {
            onSuccess: () => {
              setPendingDelete(null);
              toast({ title: tReports('deleteSuccess') });
            },
            onError: (error) => {
              toast({
                variant: 'destructive',
                title: tReports('deleteErrorTitle'),
                description: problemDescription(error),
              });
            },
          });
        }}
      />
    </section>
  );
}
