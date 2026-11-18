'use client';

import type { PostsAnalyticsMonth } from '@posts/api-client';
import { useTranslations } from 'next-intl';

import {
  MONTH_FULL_LABELS_RU,
  ensureTwelveMonths,
  formatAnalyticsRub,
  sumRevenue,
} from '../model/aggregate';

type OrdRevenueTableProps = {
  months: PostsAnalyticsMonth[];
};

export function OrdRevenueTable({ months }: OrdRevenueTableProps) {
  const t = useTranslations('analytics.revenue');
  const rows = ensureTwelveMonths(months);
  const totals = sumRevenue(rows);

  return (
    <section
      className="space-y-3 rounded-xl border border-border bg-card/40 p-4"
      aria-labelledby="analytics-revenue-title"
      data-testid="analytics-revenue"
    >
      <div>
        <h2 id="analytics-revenue-title" className="text-lg font-semibold text-foreground">
          {t('title')}
        </h2>
        <p className="text-sm text-muted-foreground">{t('description')}</p>
      </div>

      <div
        className="overflow-x-auto"
        role="region"
        aria-label={t('title')}
        tabIndex={0}
      >
        <table className="w-full min-w-[36rem] border-collapse text-sm" data-testid="analytics-revenue-table">
          <thead>
            <tr className="border-b border-border text-left text-muted-foreground">
              <th className="px-2 py-2 font-medium">{t('month')}</th>
              <th className="px-2 py-2 font-medium">{t('withVat')}</th>
              <th className="px-2 py-2 font-medium">{t('withoutVat')}</th>
              <th className="px-2 py-2 font-medium">{t('vat')}</th>
              <th className="px-2 py-2 font-medium">{t('fee3')}</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.month} className="border-b border-border/60">
                <td className="px-2 py-2">{MONTH_FULL_LABELS_RU[row.month! - 1]}</td>
                <td className="px-2 py-2 tabular-nums">
                  {formatAnalyticsRub(row.revenue?.amountWithVat)}
                </td>
                <td className="px-2 py-2 tabular-nums">
                  {formatAnalyticsRub(row.revenue?.amountWithoutVat)}
                </td>
                <td className="px-2 py-2 tabular-nums">
                  {formatAnalyticsRub(row.revenue?.vatAmount)}
                </td>
                <td className="px-2 py-2 tabular-nums">
                  {formatAnalyticsRub(row.revenue?.fee3Pct)}
                </td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr className="font-semibold text-foreground" data-testid="analytics-revenue-total">
              <td className="px-2 py-2">{t('yearTotal')}</td>
              <td className="px-2 py-2 tabular-nums">{formatAnalyticsRub(totals.amountWithVat)}</td>
              <td className="px-2 py-2 tabular-nums">
                {formatAnalyticsRub(totals.amountWithoutVat)}
              </td>
              <td className="px-2 py-2 tabular-nums">{formatAnalyticsRub(totals.vatAmount)}</td>
              <td className="px-2 py-2 tabular-nums">{formatAnalyticsRub(totals.fee3Pct)}</td>
            </tr>
          </tfoot>
        </table>
      </div>
    </section>
  );
}
