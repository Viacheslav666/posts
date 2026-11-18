import type { OrdProvider, OrdReportRow } from '@posts/api-client';

export const ORD_REPORT_STATUSES = ['draft', 'submitted', 'error'] as const;
export type OrdReportStatus = (typeof ORD_REPORT_STATUSES)[number];

export const ORD_REPORT_PAGE_SIZE = 10;

export type OrdReportFiltersState = {
  year?: number;
  month?: number;
  provider?: OrdProvider | '';
  status?: OrdReportStatus | '';
  erid?: string;
};

export type OrdReportApiQuery = {
  year?: number;
  month?: number;
  provider?: string;
  status?: string;
  erid?: string;
};

export function defaultOrdReportFilters(now = new Date()): OrdReportFiltersState {
  // Moscow-ish calendar month default without embedding Date into queryKey.
  const moscow = new Date(now.toLocaleString('en-US', { timeZone: 'Europe/Moscow' }));
  return {
    year: moscow.getFullYear(),
    month: moscow.getMonth() + 1,
    provider: '',
    status: '',
    erid: '',
  };
}

/** Stable API/queryKey payload — omit empty values; never include Date/now. */
export function ordReportFiltersToQuery(filters: OrdReportFiltersState): OrdReportApiQuery {
  const query: OrdReportApiQuery = {};
  if (typeof filters.year === 'number' && Number.isFinite(filters.year)) {
    query.year = filters.year;
  }
  if (typeof filters.month === 'number' && filters.month >= 1 && filters.month <= 12) {
    query.month = filters.month;
  }
  if (filters.provider) query.provider = filters.provider;
  if (filters.status) query.status = filters.status;
  const erid = filters.erid?.trim();
  if (erid) query.erid = erid;
  return query;
}

export function matchesOrdReportFilters(
  row: OrdReportRow,
  filters: OrdReportApiQuery,
): boolean {
  if (filters.year != null && row.periodYear !== filters.year) return false;
  if (filters.month != null && row.periodMonth !== filters.month) return false;
  if (filters.provider && row.provider !== filters.provider) return false;
  if (filters.status && row.status !== filters.status) return false;
  if (filters.erid) {
    const needle = filters.erid.toLowerCase();
    const hay = (row.erid ?? '').toLowerCase();
    if (!hay.includes(needle)) return false;
  }
  return true;
}

export function filterOrdReportRows(
  rows: readonly OrdReportRow[],
  filters: OrdReportApiQuery,
): OrdReportRow[] {
  return rows.filter((row) => matchesOrdReportFilters(row, filters));
}

export type PageSlice<T> = {
  items: T[];
  page: number;
  pageSize: number;
  total: number;
  totalPages: number;
  hasPrev: boolean;
  hasNext: boolean;
};

/** Client-side pagination over a full filtered list (API returns one page). */
export function paginateItems<T>(
  items: readonly T[],
  page: number,
  pageSize = ORD_REPORT_PAGE_SIZE,
): PageSlice<T> {
  const size = Math.max(1, pageSize);
  const total = items.length;
  const totalPages = Math.max(1, Math.ceil(total / size));
  const safePage = Math.min(Math.max(1, page), totalPages);
  const start = (safePage - 1) * size;
  return {
    items: items.slice(start, start + size) as T[],
    page: safePage,
    pageSize: size,
    total,
    totalPages,
    hasPrev: safePage > 1,
    hasNext: safePage < totalPages,
  };
}

export function yearOptions(centerYear: number, span = 3): number[] {
  const years: number[] = [];
  for (let y = centerYear - span; y <= centerYear + 1; y += 1) {
    years.push(y);
  }
  return years;
}
