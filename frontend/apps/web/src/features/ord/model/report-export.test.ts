import { describe, expect, it } from 'vitest';

import { buildOrdReportsCsv, ORD_EXPORT_CSV_HEADERS, ordExportQueryFromFilters } from './report-export';

describe('ordExportQueryFromFilters', () => {
  it('maps list filters to export query without erid', () => {
    expect(
      ordExportQueryFromFilters(
        {
          year: 2026,
          month: 11,
          provider: 'vk_ord',
          status: 'submitted',
          erid: 'should-not-export',
        },
        'xlsx',
      ),
    ).toEqual({
      format: 'xlsx',
      year: '2026',
      month: '11',
      provider: 'vk_ord',
      status: 'submitted',
    });
  });
});

describe('buildOrdReportsCsv', () => {
  it('writes BOM + headers + escaped rows', () => {
    const csv = buildOrdReportsCsv([
      {
        periodYear: 2026,
        periodMonth: 11,
        erid: 'Erid,Demo',
        creativeExternalId: 'creative:1',
        platformName: 'VK',
        impressions: 10,
        amount: 100.5,
        status: 'submitted',
        error: 'line\nbreak',
      },
    ]);
    expect(csv.startsWith('\ufeff')).toBe(true);
    expect(csv).toContain(ORD_EXPORT_CSV_HEADERS.join(','));
    expect(csv).toContain('"Erid,Demo"');
    expect(csv).toContain('"line\nbreak"');
  });
});
