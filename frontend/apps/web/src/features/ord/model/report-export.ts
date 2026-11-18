import type { OrdReportApiQuery } from './report-filters';

export type OrdExportFormat = 'csv' | 'xlsx';

/** Build export query params from list filters (export has no erid param). */
export function ordExportQueryFromFilters(
  filters: OrdReportApiQuery,
  format: OrdExportFormat,
): Record<string, string> {
  const query: Record<string, string> = { format };
  if (filters.year != null) query.year = String(filters.year);
  if (filters.month != null) query.month = String(filters.month);
  if (filters.provider) query.provider = filters.provider;
  if (filters.status) query.status = filters.status;
  return query;
}

export const ORD_EXPORT_CSV_HEADERS = [
  'PeriodYear',
  'PeriodMonth',
  'Erid',
  'CreativeExternalId',
  'Platform',
  'Impressions',
  'Amount',
  'Status',
  'Error',
] as const;

export function buildOrdReportsCsv(
  rows: Array<{
    periodYear?: number;
    periodMonth?: number;
    erid?: string;
    creativeExternalId?: string;
    platformName?: string;
    impressions?: number;
    amount?: number;
    status?: string;
    error?: string;
  }>,
): string {
  const escape = (value: string) => {
    if (/[",\n\r]/.test(value)) {
      return `"${value.replace(/"/g, '""')}"`;
    }
    return value;
  };
  const lines = [ORD_EXPORT_CSV_HEADERS.join(',')];
  for (const row of rows) {
    lines.push(
      [
        String(row.periodYear ?? ''),
        String(row.periodMonth ?? ''),
        escape(row.erid ?? ''),
        escape(row.creativeExternalId ?? ''),
        escape(row.platformName ?? ''),
        String(row.impressions ?? 0),
        String(row.amount ?? ''),
        escape(row.status ?? ''),
        escape(row.error ?? ''),
      ].join(','),
    );
  }
  return `\ufeff${lines.join('\n')}\n`;
}
