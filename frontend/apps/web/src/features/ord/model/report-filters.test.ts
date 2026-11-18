import { describe, expect, it } from 'vitest';

import type { OrdReportRow } from '@posts/api-client';

import {
  defaultOrdReportFilters,
  filterOrdReportRows,
  matchesOrdReportFilters,
  ordReportFiltersToQuery,
  paginateItems,
} from './report-filters';

const rows: OrdReportRow[] = [
  {
    periodYear: 2026,
    periodMonth: 11,
    erid: 'EridDemoVk01',
    provider: 'vk_ord',
    status: 'submitted',
  },
  {
    periodYear: 2026,
    periodMonth: 11,
    erid: 'EridDemoYa01',
    provider: 'yandex_ord',
    status: 'error',
  },
  {
    periodYear: 2026,
    periodMonth: 10,
    erid: 'EridDemoVk01',
    provider: 'vk_ord',
    status: 'draft',
  },
];

describe('ordReportFiltersToQuery', () => {
  it('omits empty values and never embeds Date', () => {
    const query = ordReportFiltersToQuery({
      year: 2026,
      month: 11,
      provider: '',
      status: 'submitted',
      erid: '  Erid  ',
    });
    expect(query).toEqual({
      year: 2026,
      month: 11,
      status: 'submitted',
      erid: 'Erid',
    });
    expect(JSON.stringify(query)).not.toMatch(/T\d{2}:\d{2}/);
  });

  it('defaults to Moscow calendar month without unstable keys', () => {
    const filters = defaultOrdReportFilters(new Date('2026-11-18T12:00:00Z'));
    expect(filters.year).toBe(2026);
    expect(filters.month).toBe(11);
    const query = ordReportFiltersToQuery(filters);
    expect(query.year).toBe(2026);
    expect(query.month).toBe(11);
  });
});

describe('matchesOrdReportFilters / filterOrdReportRows', () => {
  it('filters by provider and status', () => {
    expect(matchesOrdReportFilters(rows[0]!, { provider: 'vk_ord', status: 'submitted' })).toBe(
      true,
    );
    expect(matchesOrdReportFilters(rows[1]!, { provider: 'vk_ord' })).toBe(false);
    expect(filterOrdReportRows(rows, { year: 2026, month: 11 })).toHaveLength(2);
  });

  it('searches ERID case-insensitively', () => {
    expect(filterOrdReportRows(rows, { erid: 'demoya' })).toEqual([rows[1]]);
  });
});

describe('paginateItems', () => {
  it('slices pages and clamps out-of-range page', () => {
    const items = Array.from({ length: 23 }, (_, i) => i);
    const page1 = paginateItems(items, 1, 10);
    expect(page1.items).toEqual([0, 1, 2, 3, 4, 5, 6, 7, 8, 9]);
    expect(page1.hasNext).toBe(true);
    expect(page1.totalPages).toBe(3);

    const page3 = paginateItems(items, 3, 10);
    expect(page3.items).toEqual([20, 21, 22]);
    expect(page3.hasNext).toBe(false);

    const overflow = paginateItems(items, 99, 10);
    expect(overflow.page).toBe(3);
  });
});
