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

import {
  ANALYTICS_NETWORKS,
  MONTH_FULL_LABELS_RU,
  type NetworkOutcomePoint,
} from '../model/aggregate';

type NetworksOutcomeChartProps = {
  data: NetworkOutcomePoint[];
};

const NETWORK_COLORS: Record<string, { succeeded: string; failed: string }> = {
  vk: { succeeded: '#4C8BF5', failed: '#F87171' },
  ok: { succeeded: '#F97316', failed: '#FB7185' },
  tg: { succeeded: '#38BDF8', failed: '#F43F5E' },
  max: { succeeded: '#A78BFA', failed: '#FB923C' },
};

export function NetworksOutcomeChart({ data }: NetworksOutcomeChartProps) {
  const t = useTranslations('analytics.charts');
  const tn = useTranslations('analytics.networks');

  return (
    <section
      className="space-y-3 rounded-xl border border-border bg-card/40 p-4"
      aria-labelledby="analytics-networks-title"
      data-testid="analytics-networks-chart"
    >
      <div>
        <h2 id="analytics-networks-title" className="text-lg font-semibold text-foreground">
          {t('networksTitle')}
        </h2>
        <p className="text-sm text-muted-foreground">{t('networksDescription')}</p>
      </div>

      <div className="h-72 w-full min-w-0" role="img" aria-label={t('networksAria')}>
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={data} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" className="stroke-border" />
            <XAxis dataKey="label" tick={{ fontSize: 12 }} />
            <YAxis allowDecimals={false} tick={{ fontSize: 12 }} width={36} />
            <Tooltip
              formatter={(value, name) => {
                const key = String(name);
                const [network, outcome] = key.split('_') as [string, string];
                const label = `${tn(network)} · ${t(`outcome.${outcome}`)}`;
                return [Number(value), label];
              }}
              labelFormatter={(_, payload) => {
                const month = Number(payload?.[0]?.payload?.month);
                return MONTH_FULL_LABELS_RU[month - 1] ?? '';
              }}
            />
            <Legend
              formatter={(value) => {
                const [network, outcome] = String(value).split('_') as [string, string];
                return `${tn(network)} (${t(`outcome.${outcome}`)})`;
              }}
            />
            {ANALYTICS_NETWORKS.map((network) => (
              <Bar
                key={`${network}-succeeded`}
                dataKey={`${network}_succeeded`}
                name={`${network}_succeeded`}
                stackId={network}
                fill={NETWORK_COLORS[network]!.succeeded}
              />
            ))}
            {ANALYTICS_NETWORKS.map((network) => (
              <Bar
                key={`${network}-failed`}
                dataKey={`${network}_failed`}
                name={`${network}_failed`}
                stackId={network}
                fill={NETWORK_COLORS[network]!.failed}
              />
            ))}
          </BarChart>
        </ResponsiveContainer>
      </div>

      <table className="sr-only" data-testid="analytics-networks-table">
        <caption>{t('networksAria')}</caption>
        <thead>
          <tr>
            <th>{t('month')}</th>
            {ANALYTICS_NETWORKS.flatMap((network) => [
              <th key={`${network}-ok`}>
                {tn(network)} · {t('outcome.succeeded')}
              </th>,
              <th key={`${network}-fail`}>
                {tn(network)} · {t('outcome.failed')}
              </th>,
            ])}
          </tr>
        </thead>
        <tbody>
          {data.map((row) => (
            <tr key={row.month}>
              <td>{MONTH_FULL_LABELS_RU[row.month - 1]}</td>
              {ANALYTICS_NETWORKS.flatMap((network) => [
                <td key={`${row.month}-${network}-ok`}>{row[`${network}_succeeded`]}</td>,
                <td key={`${row.month}-${network}-fail`}>{row[`${network}_failed`]}</td>,
              ])}
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}
