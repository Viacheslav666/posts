'use client';

import { useTranslations } from 'next-intl';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';

import { MONTH_FULL_LABELS_RU, type VolumePoint } from '../model/aggregate';

type PostsVolumeChartProps = {
  data: VolumePoint[];
};

/** Solid fills — CSS variables often fail as SVG presentation attributes. */
const COLORS = {
  regular: '#2F9E6B',
  ad: '#D97706',
  repost: '#2563EB',
};

export function PostsVolumeChart({ data }: PostsVolumeChartProps) {
  const t = useTranslations('analytics.charts');

  return (
    <section
      className="space-y-3 rounded-xl border border-border bg-card/40 p-4"
      aria-labelledby="analytics-volume-title"
      data-testid="analytics-volume-chart"
    >
      <div>
        <h2 id="analytics-volume-title" className="text-lg font-semibold text-foreground">
          {t('volumeTitle')}
        </h2>
        <p className="text-sm text-muted-foreground">{t('volumeDescription')}</p>
      </div>

      <div className="h-64 w-full min-w-0" role="img" aria-label={t('volumeAria')}>
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={data} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" className="stroke-border" />
            <XAxis dataKey="label" tick={{ fontSize: 12 }} />
            <YAxis allowDecimals={false} tick={{ fontSize: 12 }} width={36} />
            <Tooltip
              formatter={(value, name) => [Number(value), t(`series.${String(name)}`)]}
              labelFormatter={(_, payload) => {
                const month = Number(payload?.[0]?.payload?.month);
                return MONTH_FULL_LABELS_RU[month - 1] ?? '';
              }}
            />
            <Legend formatter={(value) => t(`series.${String(value)}`)} />
            <Bar dataKey="regular" name="regular" fill={COLORS.regular} radius={[2, 2, 0, 0]} />
            <Bar dataKey="ad" name="ad" fill={COLORS.ad} radius={[2, 2, 0, 0]} />
            <Bar dataKey="repost" name="repost" fill={COLORS.repost} radius={[2, 2, 0, 0]} />
          </BarChart>
        </ResponsiveContainer>
      </div>

      <div className="sr-only">
        <table data-testid="analytics-volume-table">
          <caption>{t('volumeAria')}</caption>
          <thead>
            <tr>
              <th>{t('month')}</th>
              <th>{t('series.regular')}</th>
              <th>{t('series.ad')}</th>
              <th>{t('series.repost')}</th>
            </tr>
          </thead>
          <tbody>
            {data.map((row) => (
              <tr key={row.month}>
                <td>{MONTH_FULL_LABELS_RU[row.month - 1]}</td>
                <td>{row.regular}</td>
                <td>{row.ad}</td>
                <td>{row.repost}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
